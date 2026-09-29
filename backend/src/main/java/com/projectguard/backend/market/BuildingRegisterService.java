package com.projectguard.backend.market;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 등기부상 주소로 건축물대장 표제부 정보를 조회한다. juso.go.kr에서 지번(시군구코드/법정동코드/
 * 대지구분코드/본번/부번)을 얻은 뒤 건축HUB 건축물대장정보 서비스를 호출한다.
 * 주소를 지번으로 바꾸지 못했거나 건축물대장에 조회되는 표제부가 없으면 empty를 반환한다 —
 * 등록되지 않은 건물이라는 뜻일 수도, 주소 조회 실패일 수도 있어 이 자체를 위험 신호로 단정하지 않는다.
 */
@Service
public class BuildingRegisterService {

    private final JusoAddressClient jusoAddressClient;
    private final BuildingRegisterClient buildingRegisterClient;

    public BuildingRegisterService(JusoAddressClient jusoAddressClient, BuildingRegisterClient buildingRegisterClient) {
        this.jusoAddressClient = jusoAddressClient;
        this.buildingRegisterClient = buildingRegisterClient;
    }

    public Optional<BuildingInfo> lookup(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        return jusoAddressClient.search(address)
                .flatMap(buildingRegisterClient::fetchTitleInfo);
    }
}
