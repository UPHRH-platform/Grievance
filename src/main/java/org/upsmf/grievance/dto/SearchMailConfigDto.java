package org.upsmf.grievance.dto;

import java.util.Map;

public class SearchMailConfigDto {

    private String searchKeyword;

    private Map<String,Object> filter;

    private int page;

    private int size;

    private Map<String, String> sort;
}
