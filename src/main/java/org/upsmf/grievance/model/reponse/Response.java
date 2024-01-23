package org.upsmf.grievance.model.reponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@Builder
@ToString
public class Response {

    private int status;

    private Object body;

}
