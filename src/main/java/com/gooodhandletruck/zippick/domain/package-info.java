/**
 * 기능(도메인)별 패키지를 여기에 만들어요.
 *
 * <pre>
 * domain/
 * └── favorite/              예) 저장 기능
 *     ├── controller/        FavoriteController   (API 입구, @RestController)
 *     ├── service/           FavoriteService      (비즈니스 로직, @Service)
 *     ├── repository/        FavoriteRepository   (DB 접근, JpaRepository)
 *     ├── entity/            Favorite             (DB 테이블, @Entity)
 *     └── dto/               FavoriteCreateRequest, FavoriteResponse (요청/응답, record 권장)
 * </pre>
 *
 * 엔티티를 그대로 응답으로 내보내지 말고, 꼭 dto 로 바꿔서 내보내요.
 */
package com.gooodhandletruck.zippick.domain;
