package org.niiish32x.sugarsms.alert.domain.repo;

import org.niiish32x.sugarsms.alert.domain.entity.AlertEO;

import java.util.List;

/**
 * AlertRepo
 *
 * @author shenghao ni
 * @date 2025.01.17 10:27
 */
public interface AlertRepo {
    boolean saveOrUpdate(AlertEO alertEO);

    List<AlertEO> findUnFinishedAlerts(int nums);
}
