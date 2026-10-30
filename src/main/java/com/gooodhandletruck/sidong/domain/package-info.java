/**
 * 기능(도메인)별 패키지를 여기에 만들어요.
 *
 * <pre>
 * domain/
 * └── trip/                  예) 주행 기록
 *     ├── controller/        TripController       (API 입구, @RestController)
 *     ├── service/           TripService          (비즈니스 로직, @Service)
 *     ├── repository/        TripRepository       (DB 접근, JpaRepository)
 *     ├── entity/            Trip                 (DB 테이블, @Entity)
 *     └── dto/               TripCreateRequest, TripResponse (요청/응답, record 권장)
 * </pre>
 *
 * 엔티티를 그대로 응답으로 내보내지 말고, 꼭 dto 로 바꿔서 내보내요.
 */
package com.gooodhandletruck.sidong.domain;
