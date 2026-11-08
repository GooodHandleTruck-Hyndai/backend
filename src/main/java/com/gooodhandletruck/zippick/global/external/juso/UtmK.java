package com.gooodhandletruck.zippick.global.external.juso;

/**
 * UTM-K(EPSG:5179) → WGS84 위도·경도 변환. 행안부 좌표제공 API 가 UTM-K 로 줘요.
 * 횡메르카토르 역변환 (Snyder). pyproj 결과와 비교해서 1mm 이내로 맞아요
 */
public final class UtmK {

    private static final double A = 6378137.0;                 // GRS80 장반경
    private static final double F = 1 / 298.257222101;          // GRS80 편평률
    private static final double E2 = F * (2 - F);
    private static final double EP2 = E2 / (1 - E2);
    private static final double K0 = 0.9996;
    private static final double LAT0 = Math.toRadians(38);
    private static final double LON0 = Math.toRadians(127.5);
    private static final double X0 = 1_000_000;
    private static final double Y0 = 2_000_000;

    private UtmK() {
    }

    /** @return {위도, 경도} */
    public static double[] toWgs84(double x, double y) {
        double e4 = E2 * E2;
        double e6 = e4 * E2;
        double m = meridianArc(LAT0) + (y - Y0) / K0;
        double mu = m / (A * (1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256));
        double e1 = (1 - Math.sqrt(1 - E2)) / (1 + Math.sqrt(1 - E2));

        double phi1 = mu
                + (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
                + (21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
                + (151 * Math.pow(e1, 3) / 96) * Math.sin(6 * mu)
                + (1097 * Math.pow(e1, 4) / 512) * Math.sin(8 * mu);

        double sin = Math.sin(phi1);
        double cos = Math.cos(phi1);
        double tan = Math.tan(phi1);
        double c1 = EP2 * cos * cos;
        double t1 = tan * tan;
        double n1 = A / Math.sqrt(1 - E2 * sin * sin);
        double r1 = A * (1 - E2) / Math.pow(1 - E2 * sin * sin, 1.5);
        double d = (x - X0) / (n1 * K0);

        double lat = phi1 - (n1 * tan / r1) * (d * d / 2
                - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * Math.pow(d, 4) / 24
                + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * Math.pow(d, 6) / 720);
        double lon = LON0 + (d
                - (1 + 2 * t1 + c1) * Math.pow(d, 3) / 6
                + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * Math.pow(d, 5) / 120) / cos;

        return new double[] {Math.toDegrees(lat), Math.toDegrees(lon)};
    }

    private static double meridianArc(double phi) {
        double e4 = E2 * E2;
        double e6 = e4 * E2;
        return A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi
                - (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * phi)
                + (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * phi)
                - (35 * e6 / 3072) * Math.sin(6 * phi));
    }
}
