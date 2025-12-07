package com.fiap.azure.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceGroup {
    private String id;
    private String name;
    private String location;
    private String provisioningState;
    private List<Resource> resources;
}
