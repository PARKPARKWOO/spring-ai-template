package com.example.demo.common

import com.example.demo.dto.ApiResponse
import com.example.demo.dto.PageResponse
import com.example.demo.dto.ResponseCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

/**
 * API 공통 응답 사용 예시
 */

/**
 * 예시 1: 성공 응답 (데이터 포함)
 */
fun exampleSuccessResponse() {
    val data = mapOf("id" to 1, "name" to "Test")
    val response: ApiResponse<Map<String, Any>> = ApiResponse.success(
        data = data,
        message = "데이터 조회 성공"
    )
    // 결과:
    // {
    //   "code": "SUCCESS",
    //   "message": "데이터 조회 성공",
    //   "data": { "id": 1, "name": "Test" },
    //   "error": null
    // }
}

/**
 * 예시 2: 성공 응답 (데이터 없음)
 */
fun exampleSuccessResponseWithoutData() {
    val response: ApiResponse<Unit> = ApiResponse.success(
        message = "삭제가 완료되었습니다."
    )
    // 결과:
    // {
    //   "code": "SUCCESS",
    //   "message": "삭제가 완료되었습니다.",
    //   "data": null,
    //   "error": null
    // }
}

/**
 * 예시 3: 실패 응답
 */
fun exampleFailureResponse() {
    val response: ApiResponse<Nothing> = ApiResponse.failure(
        code = ResponseCode.BAD_REQUEST,
        message = "입력값이 유효하지 않습니다."
    )
    // 결과:
    // {
    //   "code": "BAD_REQUEST",
    //   "message": "입력값이 유효하지 않습니다.",
    //   "data": null,
    //   "error": null
    // }
}

/**
 * 예시 4: 페이지네이션 응답 (Spring Data Page 사용)
 */
fun examplePageResponse(page: Page<String>): PageResponse<String> {
    return PageResponse.from(
        page = page,
        message = "데이터 조회 성공"
    )
    // 결과:
    // {
    //   "code": "SUCCESS",
    //   "message": "데이터 조회 성공",
    //   "data": ["item1", "item2", ...],
    //   "pageInfo": {
    //     "page": 0,
    //     "size": 20,
    //     "totalElements": 100,
    //     "totalPages": 5,
    //     "isFirst": true,
    //     "isLast": false,
    //     "hasNext": true,
    //     "hasPrevious": false
    //   }
    // }
}

/**
 * 예시 5: 페이지네이션 응답 (수동 생성)
 */
fun examplePageResponseManual(): PageResponse<String> {
    val data = listOf("item1", "item2", "item3")
    return PageResponse.create(
        data = data,
        page = 0,
        size = 20,
        totalElements = 100L,
        message = "데이터 조회 성공"
    )
}

/**
 * 예시 6: 컨트롤러에서 사용
 */
// @RestController
// class ExampleController {
//     
//     @GetMapping("/items")
//     fun getItems(
//         @RequestParam(defaultValue = "0") page: Int,
//         @RequestParam(defaultValue = "20") size: Int
//     ): ApiResponse<PageResponse<String>> {
//         val pageable: Pageable = PageRequest.of(page, size)
//         // ... 데이터 조회 로직
//         val pageData: Page<String> = // ... 조회 결과
//         
//         return ApiResponse.success(
//             data = PageResponse.from(pageData),
//             message = "데이터 조회 성공"
//         )
//     }
//     
//     @GetMapping("/items/{id}")
//     fun getItem(@PathVariable id: Long): ApiResponse<Item> {
//         val item = // ... 조회 로직
//         return ApiResponse.success(
//             data = item,
//             message = "데이터 조회 성공"
//         )
//     }
//     
//     @PostMapping("/items")
//     fun createItem(@RequestBody request: CreateItemRequest): ApiResponse<Item> {
//         val item = // ... 생성 로직
//         return ApiResponse.success(
//             data = item,
//             message = "아이템이 생성되었습니다."
//         )
//     }
//     
//     @DeleteMapping("/items/{id}")
//     fun deleteItem(@PathVariable id: Long): ApiResponse<Unit> {
//         // ... 삭제 로직
//         return ApiResponse.success(
//             message = "아이템이 삭제되었습니다."
//         )
//     }
// }
