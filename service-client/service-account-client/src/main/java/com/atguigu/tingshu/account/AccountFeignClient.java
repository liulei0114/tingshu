package com.atguigu.tingshu.account;

import com.atguigu.tingshu.account.impl.AccountDegradeFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.vo.account.AccountDeductVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * <p>
 * 账号模块远程调用API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-account", path = "/api/account", fallback = AccountDegradeFeignClient.class)
public interface AccountFeignClient {
    /**
     * 扣减账户余额
     *
     * @param accountDeductVo
     * @return
     */
    @PostMapping("/userAccount/checkAndDeduct")
    Result checkAndDeduct(@RequestBody AccountDeductVo accountDeductVo);
}
