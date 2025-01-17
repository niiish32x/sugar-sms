package org.niiish32x.sugarsms.alert.app;

import org.niiish32x.sugarsms.alert.app.command.ProduceAlertRecordCommand;
import org.niiish32x.sugarsms.alert.app.command.SaveAlertCommand;
import org.niiish32x.sugarsms.api.alert.dto.AlertInfoDTO;
import org.niiish32x.sugarsms.api.user.dto.SuposUserDTO;
import org.niiish32x.sugarsms.common.result.Result;

import java.util.List;

/**
 * AlertService
 *
 * @author shenghao ni
 * @date 2024.12.10 9:56
 */
public interface AlertService {

     Result<List<AlertInfoDTO>> getAlertsFromSupos();

     Result <Boolean> saveAlert(SaveAlertCommand command);

     Result<List<SuposUserDTO>>  getAlertUsers();

     Boolean cleanAlertPastDays(Integer days);


     /**
      * 生成 初始化 需要发送的报警消息
      * @return
      */
     Result productAlertRecord(ProduceAlertRecordCommand alertInfoDTOS);
}
