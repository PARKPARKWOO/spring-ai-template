package com.example.demo.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 페이지네이션 정보
 */
@Schema(description = "페이지네이션 정보")
data class PageInfo(
    @field:Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
    val page: Int,

    @field:Schema(description = "페이지 크기", example = "20")
    val size: Int,

    @field:Schema(description = "전체 요소 개수", example = "100")
    val totalElements: Long,

    @field:Schema(description = "전체 페이지 개수", example = "5")
    val totalPages: Int,

    @field:Schema(description = "현재 페이지가 첫 페이지인지", example = "true")
    val isFirst: Boolean,

    @field:Schema(description = "현재 페이지가 마지막 페이지인지", example = "false")
    val isLast: Boolean,

    @field:Schema(description = "다음 페이지 존재 여부", example = "true")
    val hasNext: Boolean,

    @field:Schema(description = "이전 페이지 존재 여부", example = "false")
    val hasPrevious: Boolean,
) {
    companion object {
        /**
         * Spring Data의 Page 객체로부터 PageInfo 생성
         */
        fun from(page: org.springframework.data.domain.Page<*>): PageInfo {
            return PageInfo(
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                isFirst = page.isFirst,
                isLast = page.isLast,
                hasNext = page.hasNext(),
                hasPrevious = page.hasPrevious()
            )
        }
        
        /**
         * 수동으로 PageInfo 생성
         */
        fun create(
            page: Int,
            size: Int,
            totalElements: Long
        ): PageInfo {
            val totalPages = if (totalElements == 0L) 0 else ((totalElements - 1) / size + 1).toInt()
            return PageInfo(
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                isFirst = page == 0,
                isLast = page >= totalPages - 1,
                hasNext = page < totalPages - 1,
                hasPrevious = page > 0
            )
        }
    }
}

/**
 * 페이지네이션을 포함한 API 응답
 * 
 * @param T 응답 데이터 타입
 */
@Schema(description = "페이지네이션을 포함한 API 응답")
data class PageResponse<T>(
    @field:Schema(description = "응답 코드", example = "SUCCESS", required = true)
    val code: ResponseCode,

    @field:Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.")
    val message: String,

    @field:Schema(description = "응답 데이터 리스트")
    val data: List<T>,

    @field:Schema(description = "페이지네이션 정보")
    val pageInfo: PageInfo,
) {
    companion object {
        /**
         * Spring Data의 Page 객체로부터 PageResponse 생성
         */
        fun <T> from(
            page: org.springframework.data.domain.Page<T>,
            message: String = "요청이 성공적으로 처리되었습니다."
        ): PageResponse<T> {
            return PageResponse(
                code = ResponseCode.SUCCESS,
                message = message,
                data = page.content,
                pageInfo = PageInfo.from(page)
            )
        }
        
        /**
         * 수동으로 PageResponse 생성
         */
        fun <T> create(
            data: List<T>,
            page: Int,
            size: Int,
            totalElements: Long,
            message: String = "요청이 성공적으로 처리되었습니다."
        ): PageResponse<T> {
            return PageResponse(
                code = ResponseCode.SUCCESS,
                message = message,
                data = data,
                pageInfo = PageInfo.create(page, size, totalElements)
            )
        }
    }
}
