package com.example.testplatform.app.controller;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.app.domain.dto.DirectTestRequestDTO;
import com.example.testplatform.execution.domain.vo.DirectTestResponseVO;
import com.example.testplatform.app.service.DirectTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "直接测试", description = "无需登录和保存的即时调试工具")
@RestController
@RequestMapping("/test/direct")
public class DirectTestController {

    @Resource
    private DirectTestService directTestService;

    /**
     * 直接测试（无需登录、无需保存用例）
     * POST /api/test/direct
     */
    @Operation(summary = "直接测试", description = "临时输入接口信息，即时调试，不保存任何数据")
    @PostMapping
    public Result<DirectTestResponseVO> directTest(
            @RequestBody @Valid DirectTestRequestDTO dto) {
        DirectTestResponseVO response = directTestService.test(dto);
        return Result.success(response);
    }
}
