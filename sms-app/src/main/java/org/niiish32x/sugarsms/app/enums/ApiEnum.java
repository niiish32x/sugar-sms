package org.niiish32x.sugarsms.app.enums;

/**
 * ApiEnum
 *
 * @author shenghao ni
 * @date 2024.12.08 18:22
 */
public enum ApiEnum {

    USER_API("/open-api/auth/v2/users"),

    PESRON_API("/open-api/organization/v2/persons");


    public final String value;

    ApiEnum(String value) {
        this.value = value;
    }


}
