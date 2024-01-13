package org.upsmf.grievance.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminTextSearchDto {
    private String searchKeyword;
    private Integer size;
    private Integer page;
    private Long councilId;
    private Long departmentId;
}
