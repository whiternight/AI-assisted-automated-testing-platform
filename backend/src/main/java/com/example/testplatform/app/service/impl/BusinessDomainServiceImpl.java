package com.example.testplatform.app.service.impl;

import com.example.testplatform.app.domain.po.BusinessDomain;
import com.example.testplatform.app.mapper.BusinessDomainMapper;
import com.example.testplatform.app.service.IBusinessDomainService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 业务域表 服务实现类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Service
public class BusinessDomainServiceImpl extends ServiceImpl<BusinessDomainMapper, BusinessDomain> implements IBusinessDomainService {

}
