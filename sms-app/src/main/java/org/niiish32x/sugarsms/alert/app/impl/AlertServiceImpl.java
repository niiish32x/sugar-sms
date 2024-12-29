package org.niiish32x.sugarsms.alert.app.impl;

import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niiish32x.sugarsms.alarm.app.AlarmService;
import org.niiish32x.sugarsms.alarm.app.external.AlarmRequest;
import org.niiish32x.sugarsms.alert.domain.entity.AlertRecordEO;
import org.niiish32x.sugarsms.alert.domain.entity.MessageType;
import org.niiish32x.sugarsms.alert.domain.repo.AlertRecordRepo;
import org.niiish32x.sugarsms.api.alarm.dto.AlarmDTO;
import org.niiish32x.sugarsms.api.alert.dto.AlertInfoDTO;
import org.niiish32x.sugarsms.api.person.dto.PersonCodesDTO;
import org.niiish32x.sugarsms.api.person.dto.PersonDTO;
import org.niiish32x.sugarsms.api.user.dto.SuposUserDTO;
import org.niiish32x.sugarsms.app.cache.UserInfoCache;
import org.niiish32x.sugarsms.app.enums.ApiEnum;
import org.niiish32x.sugarsms.app.event.AlertEvent;
import org.niiish32x.sugarsms.api.alert.dto.AlertResponse;
import org.niiish32x.sugarsms.api.alarm.dto.AlarmPageResponse;
import org.niiish32x.sugarsms.api.user.dto.RoleSpecDTO;
import org.niiish32x.sugarsms.app.external.ZubrixSmsResponse;
import org.niiish32x.sugarsms.app.proxy.ZubrixSmsProxy;
import org.niiish32x.sugarsms.app.queue.AlertMessageQueue;
import org.niiish32x.sugarsms.alert.app.AlertService;
import org.niiish32x.sugarsms.app.service.PersonService;
import org.niiish32x.sugarsms.app.service.SendMessageService;
import org.niiish32x.sugarsms.app.service.UserService;
import org.niiish32x.sugarsms.common.request.SuposRequestManager;
import org.niiish32x.sugarsms.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;

/**
 * AlertServiceImpl
 *
 * @author shenghao ni
 * @date 2024.12.10 10:03
 */

@Service
@Slf4j
public class AlertServiceImpl implements AlertService {

    private final String DEFAULT_COMPANY_CODE = "default_org_company";
    private final String SYSTEM_ROLE_CODE = "systemRole";
    private final String NORMAL_ROLE_CODE = "normalRole";

    private final String SUGAR_ALERT_EMAIL_SUBJECT = "sugar-plant-alert";

    static ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(
            100,
            200,
            100,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000),
            new ThreadPoolExecutor.CallerRunsPolicy() // 任务满后不拒绝执行 每次 预警短信 争取全部送达
    );


    // 防止重复发送
    static  ConcurrentHashMap <String,String> visited = new ConcurrentHashMap<>();
    // sms + 消息ID + phone
    private final String PHONE_KEY = "sms%s%s";

    // email + 消息ID + email
    private final String EMAIL_KEY = "email%s%s";


    @Autowired
    AlarmService alarmService;

    @Resource
    AlertRecordRepo alertRecordRepo;

    @Autowired
    AlertMessageQueue alertMessageQueue;


    @Autowired
    ApplicationEventPublisher publisher;

    @Resource
    ZubrixSmsProxy zubrixSmsProxy;


    @Resource
    UserService userService;

    @Resource
    UserInfoCache userInfoCache;

    @Resource
    PersonService personService;

    @Resource
    SuposRequestManager requestManager;


    @Resource
    SendMessageService sendMessageService;

    @Override
    public List<AlertRecordEO> getAllAlertRecords() {
        return alertRecordRepo.find();
    }

    @Override
    public Result <List<AlertInfoDTO>> getAlertsFromSupos() {
        Map<String, String> headerMap = new HashMap<>();
        Map<String, String> queryMap = new HashMap<>();
        HttpResponse response = requestManager.suposApiGet(ApiEnum.ALERT_API.value, headerMap, queryMap);
        AlertResponse alertResponse = JSON.parseObject(response.body(), AlertResponse.class);
        return alertResponse.getCode() == 200 ? Result.success(alertResponse.getAlerts())  : Result.error("查询报警信息失败") ;
    }


    @Override
    public Result <ZubrixSmsResponse> notifyTest() {
        Result<List<AlertInfoDTO>> alertsResult = getAlertsFromSupos();

        if(!alertsResult.isSuccess()) {
            log.error("获取报警信息异常");
            return Result.error("获取报警信息异常");
        }

        List<AlertInfoDTO> alertInfoDTOS = alertsResult.getData();

        if(alertInfoDTOS == null || alertInfoDTOS.isEmpty()) {
            log.info("不存在报警信息 不需要报警");
            return Result.success(null);
        }


        Result<List<SuposUserDTO>> res = userService.getUsersFromSupos("default_org_company", "sugarsms");

        List<SuposUserDTO> sugasmsUsers = res.getData();

        if(sugasmsUsers.isEmpty()) {
            log.warn("不存在sugarsms 角色权限的报警对象");
            return Result.success(null);
        }

        AlertInfoDTO alertInfoDTO = alertInfoDTOS.get(0);
        String text = zubrixSmsProxy.formatTextContent(alertInfoDTO);
        SuposUserDTO userDTO = sugasmsUsers.get(0);
        String phoneNumber = userInfoCache.nameToPhone.getIfPresent(userDTO.getPersonCode());

            if(phoneNumber == null) {
                PersonDTO person = personService.getOnePersonByPersonCode(
                        PersonCodesDTO.builder()
                                .personCodes(Arrays.asList(userDTO.getPersonCode()))
                                .build()
                ).getData();
                phoneNumber = person.getPhone();
                userInfoCache.load();
            }


        String finalPhoneNumber = phoneNumber;
        ZubrixSmsResponse zubrixSmsResponse = sendMessageService.sendOneZubrixSmsMessage(finalPhoneNumber, text).getData();
        log.info("通知内容 {} ",text);

        log.info("person: {} phone:{} 通知成功",userDTO.getPersonName(),phoneNumber);

        return zubrixSmsResponse.getErrorCode() == 0 ?  Result.success(zubrixSmsResponse) : Result.error("通知异常");
    }

    @Override
    public void publishAlertEvent() {
        AlertEvent event = new AlertEvent(this);
        publisher.publishEvent(event);
        log.info("发布 报警消息事件");
    }

    @Override
    public Result <Boolean> notifyUserByEmail(SuposUserDTO userDTO, AlertInfoDTO alertInfoDTO ) {

        String email = UserInfoCache.nameToEmail.getIfPresent(userDTO.getPersonCode());

        if (email == null) {
            PersonDTO person = personService.getOnePersonByPersonCode(
                    PersonCodesDTO.builder()
                            .personCodes(Arrays.asList(userDTO.getPersonCode()))
                            .build()
            ).getData();
            email = person.getEmail();
            userInfoCache.load();
        }


        String key = String.format(EMAIL_KEY, alertInfoDTO.getId(),email);

        if (visited.containsKey(key)) {
            log.info("alertInfoDTO {} 已发送成功不再重新发送  {}",alertInfoDTO.getId() , email);
            return Result.success(true);
        }

        boolean saveRes = false;
        AlertRecordEO recordEO = null;


        Result<List<AlarmDTO>> alertsSpecFromSupos = alarmService.getAlarmsFromSupos(AlarmRequest.builder()
                        .attributeEnName(alertInfoDTO.getSourcePropertyName())
                .build());


        if(!alertsSpecFromSupos.isSuccess()) {
            log.error("获取alertsSpecFromSupos 报警详情信息异常");
            return Result.error("获取alertsSpecFromSupos 报警详情信息异常");
        }

        AlarmDTO alarmDTO = alertsSpecFromSupos.getData().get(0);

        String text = zubrixSmsProxy.formatTextContent(alertInfoDTO, alarmDTO.getLimitValue());


        if (StringUtils.isNotBlank(email)) {
            boolean res = sendMessageService.sendEmail(email, SUGAR_ALERT_EMAIL_SUBJECT, text);

            if(res) {
                // 本次发送成功后 进行标记 不再进行二次发送
                visited.put(key, "1");

                recordEO = buildAlertRecordEO(alertInfoDTO, userDTO.getUsername(), null, email, MessageType.EMAIL, text, true);
                saveRes =  alertRecordRepo.save(recordEO);
            }else {
                recordEO = buildAlertRecordEO(alertInfoDTO, userDTO.getUsername(), null, email, MessageType.EMAIL, text, false);
                saveRes =  alertRecordRepo.save(recordEO);
            }

            log.info("alert: {} 通知成功 -> email:  {}",alertInfoDTO.getId() , email);

        }

        if (!saveRes) {
            assert recordEO != null;
            log.error("email alert: {} {} 数据库 落盘失败",recordEO.getAlertId(),recordEO.getEmail());
        }

        return saveRes ?  Result.success(saveRes) : Result.error("记录保存失败");
    }

    @Override
    public Result<Boolean> notifyUserBySms(SuposUserDTO userDTO, AlertInfoDTO alertInfoDTO) {

        Result<List<AlarmDTO>> alertsSpecFromSupos = alarmService.getAlarmsFromSupos(AlarmRequest.builder()
                .attributeEnName(alertInfoDTO.getSourcePropertyName())
                .build());

        if(!alertsSpecFromSupos.isSuccess()) {
            log.error("获取alertsSpecFromSupos 报警详情信息异常");
            return Result.error("获取alertsSpecFromSupos 报警详情信息异常");
        }

        AlarmDTO alarmDTO = alertsSpecFromSupos.getData().get(0);

        String text = zubrixSmsProxy.formatTextContent(alertInfoDTO, alarmDTO.getLimitValue());

        String phoneNumber = userInfoCache.nameToPhone.getIfPresent(userDTO.getPersonCode());

        if(phoneNumber == null) {
            PersonDTO person = personService.getOnePersonByPersonCode(
                    PersonCodesDTO.builder()
                            .personCodes(Arrays.asList(userDTO.getPersonCode()))
                            .build()
            ).getData();
            phoneNumber = person.getPhone();
            userInfoCache.load();
        }

        String key = String.format(PHONE_KEY,alertInfoDTO.getId(),phoneNumber);

        if (visited.containsKey(key)) {
            log.info("alert 已经sms发送过无需再次通知 {} -> {}",alertInfoDTO.getId(),phoneNumber);
            return Result.success(true);
        }

        Result<ZubrixSmsResponse> smsResp = sendMessageService.sendOneZubrixSmsMessage(phoneNumber, text);

        if (smsResp.isSuccess()) {
            visited.put(key,"1");
            log.info("alert {} sms 通知成功 -> {}",alertInfoDTO.getId(),phoneNumber);
        }


        boolean saveRes;
        AlertRecordEO recordEO = null;
        if (smsResp.isSuccess()) {
            recordEO = buildAlertRecordEO(alertInfoDTO, userDTO.getUsername(), phoneNumber, null, MessageType.SMS, text, true);
            saveRes = alertRecordRepo.save(recordEO);
        }else {
            recordEO = buildAlertRecordEO(alertInfoDTO, userDTO.getUsername(), phoneNumber, null, MessageType.SMS, text, false);
            saveRes =  alertRecordRepo.save(recordEO);
        }

        if(!saveRes) {
            log.error("sms alert {} {} 数据库 落盘失败",recordEO.getAlertId(),recordEO.getPhone());
        }

        return saveRes ? Result.success(saveRes) : Result.error("记录保存失败");
    }

    @Override
    public Boolean cleanAlertPastDays(Integer days) {
        List<AlertRecordEO> alertsBeforeDays = alertRecordRepo.findAlertsBeforeDays(days);

        List<Long> ids = new ArrayList<>();

        for (AlertRecordEO alertRecordEO : alertsBeforeDays) {
            ids.add(alertRecordEO.getId());
        }

        return alertRecordRepo.remove(ids);
    }

    @Override
    public void consumeAlertEvent() {
        // 获取角色列表并处理异常
        Result<List<RoleSpecDTO>> roleListFromSupos = userService.getRoleListFromSupos(DEFAULT_COMPANY_CODE);
        if (!roleListFromSupos.isSuccess()) {
            throw new RuntimeException("Failed to get role list from Supos: " + roleListFromSupos.getMessage());
        }
        List<RoleSpecDTO> roleSpecDTOList = roleListFromSupos.getData();

        List<SuposUserDTO> userLists = new ArrayList<>();

        for (RoleSpecDTO roleSpecDTO : roleSpecDTOList) {

            // 管理员 或者 无效 角色 跳过
            if (roleSpecDTO != null &&  (StringUtils.equals(roleSpecDTO.getRoleCode(), NORMAL_ROLE_CODE) ||  StringUtils.equals(roleSpecDTO.getRoleCode(), SYSTEM_ROLE_CODE)|| roleSpecDTO.getValid() == 0)) {
                continue;
            }


            Result<List<SuposUserDTO>> usersFromSupos = userService.getUsersFromSupos(DEFAULT_COMPANY_CODE, roleSpecDTO.getRoleCode());
            if (!usersFromSupos.isSuccess()) {
                throw new RuntimeException("Failed to get users from Supos: " + usersFromSupos.getMessage());
            }
            userLists.addAll(usersFromSupos.getData());
        }



        while (!alertMessageQueue.isEmpty()) {
            AlertInfoDTO alertInfoDTO = alertMessageQueue.poll();
            if (alertInfoDTO == null) {
                continue; // 处理空值
            }

            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (SuposUserDTO userDTO : userLists) {
                log.info("开始发送预警消息 -> role: {}  username: {}",userDTO.getUserRoleList().get(0),userDTO.getUsername());
                futures.add(CompletableFuture.supplyAsync(() -> notifyUserBySms(userDTO, alertInfoDTO), poolExecutor));
                futures.add(CompletableFuture.supplyAsync(() -> notifyUserByEmail(userDTO, alertInfoDTO), poolExecutor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join(); // 等待所有任务完成
        }


         for (AlertRecordEO alertRecordEO :  alertRecordRepo.find(MessageType.SMS, false)) {
             Result<ZubrixSmsResponse> smsResponseResult = sendMessageService.sendOneZubrixSmsMessage(alertRecordEO.getPhone(), alertRecordEO.getContent());
             if (smsResponseResult.isSuccess()) {
                 alertRecordEO.setSendTime(new Date());
                 alertRecordEO.setStatus(true);
                 alertRecordRepo.save(alertRecordEO);
             }
         }

         for (AlertRecordEO alertRecordEO :  alertRecordRepo.find(MessageType.EMAIL, false)) {
             boolean sendRes = sendMessageService.sendEmail(alertRecordEO.getEmail(), SUGAR_ALERT_EMAIL_SUBJECT, alertRecordEO.getContent());
             if (sendRes) {
                 alertRecordEO.setSendTime(new Date());
                 alertRecordEO.setStatus(true);
                 alertRecordRepo.save(alertRecordEO);
             }
         }
    }

    private AlertRecordEO buildAlertRecordEO(AlertInfoDTO alertInfoDTO,String username,String phone,String email,MessageType type,String text,Boolean status) {
        if (type == MessageType.SMS) {
            return AlertRecordEO.builder()
                    .type(MessageType.SMS)
                    .alertId(alertInfoDTO.getId())
                    .username(username)
                    .content(text)
                    .sendTime(new Date())
                    .phone(phone)
                    .status(status)
                    .build();
        }else {
            return AlertRecordEO.builder()
                    .type(MessageType.EMAIL)
                    .alertId(alertInfoDTO.getId())
                    .username(username)
                    .content(text)
                    .sendTime(new Date())
                    .email(email)
                    .status(status)
                    .build();
        }
    }
}
