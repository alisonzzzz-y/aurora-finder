package com.aurora.observation.monitoring;

import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import java.io.StringReader;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

@Service
public class MonitoringService {
    public record Point(Instant at, double value, String source) {}
    public record Series(String id, String unit, String status, List<Point> points) {}
    public record Solar(Instant retrievedAt, String sourceUrl, List<Series> series) {}
    public record Regional(Instant retrievedAt, Instant updatedAt, String status, String station,
            String alert, String color, String attribution, String license, String sourceUrl, List<Point> points) {}
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private Solar solarCache;
    private Regional regionalCache;
    private Instant solarRetry = Instant.MIN, regionalRetry = Instant.MIN;
    private static final String AW = "https://aurorawatch-api.lancs.ac.uk/0.2/";
    public MonitoringService(HttpClient client, ObjectMapper mapper, Clock clock) {
        this.client=client; this.mapper=mapper; this.clock=clock;
    }
    private String get(String url) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "AuroraFinder/1.0 (https://aurora-finder.vercel.app)").GET().build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200) throw new IllegalStateException("Monitoring source unavailable");
        return response.body();
    }
    public synchronized Solar solar() {
        Instant now=clock.instant();
        if(now.isBefore(solarRetry) && solarCache!=null) return solarCache;
        List<Series> result=new ArrayList<>();
        try {
            String body=get("https://services.swpc.noaa.gov/json/rtsw/rtsw_mag_1m.json");
            result.add(parseSolar(body,"bz_gsm","Bz","nT",now));
            result.add(parseSolar(body,"bt","Bt","nT",now));
        } catch(Exception e) { interrupted(e); result.add(empty("Bz","nT")); result.add(empty("Bt","nT")); }
        try { result.add(parseSolar(get("https://services.swpc.noaa.gov/json/rtsw/rtsw_wind_1m.json"),"proton_speed","speed","km/s",now)); }
        catch(Exception e) { interrupted(e); result.add(empty("speed","km/s")); }
        solarCache=new Solar(now,"https://www.spaceweather.gov/products/solar-wind",List.copyOf(result));
        solarRetry=now.plusSeconds(180);
        return solarCache;
    }
    private static void interrupted(Exception e) { if(e instanceof InterruptedException) Thread.currentThread().interrupt(); }
    private static Series empty(String id,String unit) {return new Series(id,unit,"UNAVAILABLE",List.of());}
    Series parseSolar(String body,String field,String id,String unit,Instant now) {
        JsonNode root=mapper.readTree(body);
        if(!root.isArray()) throw new IllegalArgumentException("Invalid solar wind response");
        TreeMap<Instant,Point> points=new TreeMap<>();
        for(JsonNode row:root) {
            if(!row.path("active").asBoolean(false) || !row.path("overall_quality").isNumber()
                    || row.path("overall_quality").intValue()!=0 || !row.path(field).isNumber()) continue;
            try {
                Instant at=timestamp(row.path("time_tag").asText());
                double value=row.path(field).doubleValue();
                if(at.isAfter(now.plusSeconds(60))||at.isBefore(now.minusSeconds(86400))||!Double.isFinite(value)) continue;
                if((field.equals("proton_speed")&&(value<=0||value>5000)) || (field.equals("bt")&&(value<0||value>1000)) || Math.abs(value)>10000) continue;
                points.put(at,new Point(at,value,row.path("source").asText()));
            } catch(RuntimeException ignored) { /* One bad sample must not erase the valid series. */ }
        }
        String status=points.isEmpty()?"UNAVAILABLE":points.lastKey().isBefore(now.minusSeconds(900))?"STALE":"CURRENT";
        return new Series(id,unit,status,List.copyOf(points.values()));
    }
    public synchronized Regional regional() {
        Instant now=clock.instant();
        if(now.isBefore(regionalRetry)&&regionalCache!=null) return regionalCache;
        try {
            String activity=get(AW+"status/alerting-site-activity.xml");
            Element root=xml(activity).getDocumentElement();
            String site=root.getAttribute("site_id");
            if(!site.matches("site:[A-Za-z0-9]+:[A-Za-z0-9]+")) throw new IllegalArgumentException("Invalid station");
            String[] ids=site.split(":");
            String meta=get(AW+"project/"+ids[1].toLowerCase(Locale.ROOT)+"/"+ids[2].toLowerCase(Locale.ROOT)+".xml");
            regionalCache=parseRegional(activity,meta,get(AW+"status-descriptions.xml"),now);
        } catch(Exception e) {
            interrupted(e);
            regionalCache=new Regional(now,null,"UNAVAILABLE","AuroraWatch UK",null,null,"","","https://aurorawatch.lancs.ac.uk/",List.of());
        }
        regionalRetry=now.plusSeconds(180);
        return regionalCache;
    }
    Regional parseRegional(String activity,String metadata,String descriptions,Instant now) throws Exception {
        Document document=xml(activity), meta=xml(metadata), levels=xml(descriptions);
        Element root=document.getDocumentElement();
        String stationId=root.getAttribute("site_id");
        var siteNodes=meta.getElementsByTagName("site");
        if(siteNodes.getLength()!=1 || !((Element)siteNodes.item(0)).getAttribute("id").equals(stationId))
            throw new IllegalArgumentException("Station mismatch");
        TreeMap<Instant,Point> points=new TreeMap<>();
        TreeMap<Instant,String> statuses=new TreeMap<>();
        var rows=document.getElementsByTagName("activity");
        for(int i=0;i<rows.getLength();i++) {
            Element row=(Element)rows.item(i);
            Instant at=timestamp(text(row,"datetime")); double value=Double.parseDouble(text(row,"value"));
            if(!Double.isFinite(value)||value<0||at.isAfter(now)||at.isBefore(now.minusSeconds(90000))) continue;
            points.put(at,new Point(at,value,stationId)); statuses.put(at,row.getAttribute("status_id"));
        }
        if(points.isEmpty()) throw new IllegalArgumentException("No regional samples");
        Instant updated=timestamp(text((Element)document.getElementsByTagName("updated").item(0),"datetime"));
        String alert=statuses.lastEntry().getValue(), color=null;
        var statusesXml=levels.getElementsByTagName("status");
        for(int i=0;i<statusesXml.getLength();i++) {
            Element status=(Element)statusesXml.item(i);
            if(status.getAttribute("id").equals(alert)) color=text(status,"color");
        }
        if(color==null || !color.matches("#[a-fA-F0-9]{6}")) throw new IllegalArgumentException("Unknown alert");
        Element station=(Element)siteNodes.item(0);
        return new Regional(now,updated,updated.isBefore(now.minusSeconds(900))||points.lastKey().isBefore(now.minusSeconds(7200))?"STALE":"CURRENT",
                text(station,"location"),alert,color,text(station,"attribution"),text(station,"license"),"https://aurorawatch.lancs.ac.uk/",List.copyOf(points.values()));
    }
    static Instant timestamp(String value) {
        String normalized=value.replaceAll("([+-]\\d{2})(\\d{2})$","$1:$2");
        return normalized.endsWith("Z")||normalized.matches(".*[+-]\\d{2}:\\d{2}$")?OffsetDateTime.parse(normalized).toInstant():LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
    }
    private static String text(Element element,String tag) {
        var nodes=element.getElementsByTagName(tag);
        return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();
    }
    static Document xml(String source) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(source)));
    }
}
