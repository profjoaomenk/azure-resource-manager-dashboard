package com.fiap.azure.dto;

import com.fiap.azure.model.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceGroupDTO {
    private String id;
    private String name;
    private String location;
    private String provisioningState;
    private int resourceCount;
    private List<Resource> resources;
}
