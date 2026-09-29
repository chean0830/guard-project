package com.projectguard.backend.market;

/**
 * @param roadAddr 도로명주소
 * @param admCd    10자리 법정동코드. 앞 5자리가 실거래가 API의 LAWD_CD(시군구코드)다.
 * @param bdNm     건물명(공동주택명)
 * @param mtYn     산여부 ("0"=대지, "1"=산). 건축물대장 API의 대지구분코드(platGbCd)와 값이 같다.
 * @param lnbrMnnm 지번 본번
 * @param lnbrSlno 지번 부번
 */
public record JusoAddressResult(
        String roadAddr,
        String admCd,
        String bdNm,
        String mtYn,
        String lnbrMnnm,
        String lnbrSlno
) {

    public String lawdCd() {
        return admCd != null && admCd.length() >= 5 ? admCd.substring(0, 5) : null;
    }

    public String sigunguCd() {
        return admCd != null && admCd.length() >= 5 ? admCd.substring(0, 5) : null;
    }

    public String bjdongCd() {
        return admCd != null && admCd.length() >= 10 ? admCd.substring(5, 10) : null;
    }

    public String platGbCd() {
        return "1".equals(mtYn) ? "1" : "0";
    }

    public String bun() {
        return zeroPad(lnbrMnnm);
    }

    public String ji() {
        return zeroPad(lnbrSlno);
    }

    private String zeroPad(String value) {
        if (value == null || value.isBlank()) {
            return "0000";
        }
        return String.format("%4s", value).replace(' ', '0');
    }
}
