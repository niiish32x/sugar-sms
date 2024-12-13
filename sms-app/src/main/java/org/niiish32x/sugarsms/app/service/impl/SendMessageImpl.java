package org.niiish32x.sugarsms.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.niiish32x.sugarsms.app.cache.UserPhoneCache;
import org.niiish32x.sugarsms.app.external.ZubrixSmsRequest;
import org.niiish32x.sugarsms.app.external.ZubrixSmsResponse;
import org.niiish32x.sugarsms.app.proxy.ZubrixSmsProxy;
import org.niiish32x.sugarsms.app.service.PersonService;
import org.niiish32x.sugarsms.app.service.SendMessageService;
import org.niiish32x.sugarsms.app.service.UserService;
import org.niiish32x.sugarsms.common.result.Result;
import org.niiish32x.sugarsms.common.result.ResultCodeEnum;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * SendMessageImpl
 *
 * @author shenghao ni
 * @date 2024.12.09 9:42
 */

@Service
@Slf4j
public class SendMessageImpl implements SendMessageService {

    @Resource
    UserPhoneCache userPhoneCache;

    @Resource
    ZubrixSmsProxy zubrixSmsProxy;

    @Resource
    UserService userService;

    @Resource
    PersonService personService;


    @Override
    public Result <ZubrixSmsResponse >sendOneZubrixSms(String number, String text) {
        ZubrixSmsRequest smsRequest = zubrixSmsProxy.buildRequest(number, text);
        String url = zubrixSmsProxy.buildUrl(smsRequest);
        ZubrixSmsResponse messageResponse = zubrixSmsProxy.send(url);
        return messageResponse.getErrorCode() == 0 ? Result.build(messageResponse,ResultCodeEnum.SUCCESS) : Result.build(messageResponse,ResultCodeEnum.FAIL);
    }
}
