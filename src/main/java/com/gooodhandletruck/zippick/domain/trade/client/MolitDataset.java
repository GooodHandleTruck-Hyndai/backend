package com.gooodhandletruck.zippick.domain.trade.client;

import com.gooodhandletruck.zippick.domain.building.entity.BuildingType;

/**
 * 국토부 매매 실거래 API 종류. 응답 모양은 같고 주소·건물명 칸만 달라요. (#27 에서 추가: 오피스텔)
 *
 * 원룸(연립다세대 RTMSDataSvcRHTrade, 단독다가구 RTMSDataSvcSHTrade)은 팀이 범위를 정하면 여기에 추가해요.
 *
 * @param buildingType 저장할 건물 유형
 * @param path         baseUrl(https://apis.data.go.kr/1613000) 뒤에 붙는 경로
 * @param nameTag      건물명 태그 — 아파트 aptNm, 오피스텔 offiNm
 */
public enum MolitDataset {
    APT(BuildingType.APT, "/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade", "aptNm"),
    OFFICETEL(BuildingType.OFFICETEL, "/RTMSDataSvcOffiTrade/getRTMSDataSvcOffiTrade", "offiNm");

    private final BuildingType buildingType;
    private final String path;
    private final String nameTag;

    MolitDataset(BuildingType buildingType, String path, String nameTag) {
        this.buildingType = buildingType;
        this.path = path;
        this.nameTag = nameTag;
    }

    public BuildingType buildingType() {
        return buildingType;
    }

    public String path() {
        return path;
    }

    public String nameTag() {
        return nameTag;
    }

    public static MolitDataset of(BuildingType type) {
        for (MolitDataset dataset : values()) {
            if (dataset.buildingType == type) {
                return dataset;
            }
        }
        throw new IllegalArgumentException("국토부 실거래 API 가 없는 건물 유형이에요: " + type);
    }
}
