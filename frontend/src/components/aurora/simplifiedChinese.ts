// MapTiler offers only language=zh, which occasionally returns a Traditional name such as 巴倫支海. These are the
// Traditional characters common in transliterated place names; an unlisted one stays readable, just not converted.
const pairs = '倫伦蘭兰爾尔亞亚歐欧羅罗維维諾诺薩萨達达紐纽約约華华聖圣灣湾島岛嶼屿陸陆區区縣县鄉乡鎮镇層层東东馬马麥麦濱滨瀋沈'
  + '漢汉遼辽龍龙遠远運运穌稣剛刚烏乌幹干賓宾來来萊莱雲云貝贝賴赖連连門门內内開开關关蘇苏單单總总邊边階阶務务廣广場场'
  + '瑪玛臘腊盧卢魯鲁須须黃黄湯汤橋桥蓋盖傑杰喬乔瓊琼紅红緯纬綠绿鄰邻極极際际聯联國国語语義义實实觀观測测線线寧宁廈厦'
  + '澤泽淺浅'
const simplified = new Map(Array.from({ length: pairs.length / 2 }, (_, index) => [pairs[index * 2], pairs[index * 2 + 1]]))

export function toSimplifiedChinese(text: string) {
  return Array.from(text, character => simplified.get(character) ?? character).join('')
}
