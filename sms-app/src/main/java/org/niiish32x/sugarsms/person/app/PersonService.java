package org.niiish32x.sugarsms.person.app;


import org.niiish32x.sugarsms.api.person.dto.PersonCodesDTO;
import org.niiish32x.sugarsms.api.person.dto.PersonDTO;
import org.niiish32x.sugarsms.api.person.dto.PersonsResponse;
import org.niiish32x.sugarsms.api.person.dto.SuposPersonUpdateRequest;
import org.niiish32x.sugarsms.common.result.Result;
import org.niiish32x.sugarsms.person.app.external.PersonPageQueryRequest;

import java.util.List;

/**
 * PersonService
 *
 * @author shenghao ni
 * @date 2024.12.08 18:40
 */
public interface PersonService {


    /**
     * 分页查询 person 从supos
     * @param request
     * @return
     */
    Result<List<PersonDTO>> searchPeronFromSupos(PersonPageQueryRequest request);


    Result<PersonDTO>  getOnePersonByPersonCode(PersonCodesDTO personCodesDTO);


    Result addPerson(String code);

    Result updatePerson(SuposPersonUpdateRequest request);

    Result mockPerson();

}
