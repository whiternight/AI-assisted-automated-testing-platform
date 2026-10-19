package com.example.testplatform.app.service;

import com.example.testplatform.app.domain.dto.DirectTestRequestDTO;
import com.example.testplatform.execution.domain.vo.DirectTestResponseVO;

public interface DirectTestService {

    /**
     * 直接测试执行
     * 不保存任何数据，纯内存执行
     */
    DirectTestResponseVO test(DirectTestRequestDTO dto);
}
