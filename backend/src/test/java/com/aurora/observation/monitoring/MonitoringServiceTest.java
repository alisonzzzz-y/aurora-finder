package com.aurora.observation.monitoring;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class MonitoringServiceTest {
    private static final Instant NOW=Instant.parse("2026-10-11T15:40:00Z");
    private final MonitoringService service=new MonitoringService(HttpClient.newHttpClient(),new ObjectMapper(),Clock.fixed(NOW,ZoneOffset.UTC));
    @Test void solarUsesOnlyActiveGoodSamplesAndSortsWithoutFillingMissingMinutes() {
        var result=service.parseSolar("""
          [
          {"time_tag":"2026-10-11T15:38:00","active":true,"overall_quality":0,"bz_gsm":-3.4,"source":"SOLAR-1"},
          {"time_tag":"2026-10-11T15:37:00","active":false,"overall_quality":0,"bz_gsm":99},
          {"time_tag":"2026-10-11T15:36:00","active":true,"overall_quality":1,"bz_gsm":99},
          {"time_tag":"2026-10-11T15:35:00","active":true,"overall_quality":0,"bz_gsm":null},
          {"time_tag":"2026-10-11T15:30:00","active":true,"overall_quality":0,"bz_gsm":2.1,"source":"SOLAR-1"},
          {"time_tag":"2026-10-12T15:30:00","active":true,"overall_quality":0,"bz_gsm":2.1},
          {"time_tag":"2026-10-11T15:31:00","active":true,"bz_gsm":2.1}
          ]
          ""","bz_gsm","Bz","nT",NOW);
        assertEquals("CURRENT",result.status()); assertEquals(2,result.points().size());
        assertEquals(2.1,result.points().getFirst().value()); assertEquals(-3.4,result.points().getLast().value());
        assertEquals("SOLAR-1",result.points().getLast().source());
    }
    @Test void oldSolarMeasurementsAreNotPresentedAsLive() {
        var result=service.parseSolar("[{\"time_tag\":\"2026-10-11T12:00:00Z\",\"active\":true,\"overall_quality\":0,\"proton_speed\":450}]","proton_speed","speed","km/s",NOW);
        assertEquals("STALE",result.status());
        assertEquals("UNAVAILABLE",service.parseSolar("[]","bt","Bt","nT",NOW).status());
    }
    @Test void xmlDoesNotResolveExternalEntitiesOrDtd() throws Exception {
        var doc=MonitoringService.xml("<!DOCTYPE root [<!ENTITY test SYSTEM 'file:///etc/passwd'>]><root>&test;</root>");
        assertEquals("",doc.getDocumentElement().getTextContent());
    }
    @Test void regionalRetainsStationIdentitySourceAlertAndAttribution() throws Exception {
        String activity="""
          <!DOCTYPE site_activity SYSTEM "https://invalid.example/never-fetch.dtd">
          <site_activity site_id="site:AWN:SUM"><updated><datetime>2026-10-11T15:39:32+0000</datetime></updated>
          <activity status_id="yellow"><datetime>2026-10-11T15:00:00+0000</datetime><value>60</value></activity>
          <activity status_id="green"><datetime>2026-10-11T14:00:00+0000</datetime><value>20</value></activity></site_activity>
          """;
        String meta="<project><site id='site:AWN:SUM'><location>Sumburgh</location><attribution>Lancaster University</attribution><license>CC BY-NC-SA</license></site></project>";
        String levels="<status_list><status id='yellow'><color>#ffff00</color></status></status_list>";
        var result=service.parseRegional(activity,meta,levels,NOW);
        assertEquals("yellow",result.alert()); assertEquals("#ffff00",result.color());
        assertEquals("Lancaster University",result.attribution()); assertEquals(2,result.points().size());
        assertEquals("CURRENT",result.status());
        assertEquals("STALE",service.parseRegional(activity,meta,levels,NOW.plusSeconds(10800)).status());
        assertThrows(IllegalArgumentException.class,()->service.parseRegional(activity,meta.replace("site:AWN:SUM","site:AWN:OTHER"),levels,NOW));
    }
}
