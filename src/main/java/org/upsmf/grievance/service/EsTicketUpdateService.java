package org.upsmf.grievance.service;

import org.upsmf.grievance.dto.UpdateUserDto;

public interface EsTicketUpdateService {

    void updateEsTicketByUserId(UpdateUserDto updateUserDto);

    void updateJunkByEsTicketByUserId(UpdateUserDto updateUserDto);
}
