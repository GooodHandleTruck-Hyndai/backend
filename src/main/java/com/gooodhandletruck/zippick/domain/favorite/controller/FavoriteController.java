package com.gooodhandletruck.zippick.domain.favorite.controller;

import com.gooodhandletruck.zippick.domain.favorite.dto.FavoriteCreateRequest;
import com.gooodhandletruck.zippick.domain.favorite.dto.FavoriteResponse;
import com.gooodhandletruck.zippick.domain.favorite.service.FavoriteService;
import com.gooodhandletruck.zippick.global.auth.CurrentDevice;
import com.gooodhandletruck.zippick.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "4. 저장", description = "관심 건물 저장하기 / 목록 / 취소")
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "건물 저장하기", description = "에러: B001 없는 건물, F001 이미 저장한 건물")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FavoriteResponse> save(
            @Parameter(hidden = true) CurrentDevice device,
            @Valid @RequestBody FavoriteCreateRequest request) {
        return ApiResponse.ok(favoriteService.save(device, request.buildingId()));
    }

    @Operation(summary = "저장 목록", description = "최근에 저장한 순서. 없으면 빈 목록")
    @GetMapping
    public ApiResponse<List<FavoriteResponse>> getFavorites(@Parameter(hidden = true) CurrentDevice device) {
        return ApiResponse.ok(favoriteService.getFavorites(device));
    }

    @Operation(summary = "저장 취소", description = "에러: F002 저장하지 않은 건물")
    @DeleteMapping("/{buildingId}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) CurrentDevice device,
            @Parameter(description = "건물 ID", example = "1") @PathVariable Long buildingId) {
        favoriteService.delete(device, buildingId);
        return ApiResponse.ok();
    }
}
