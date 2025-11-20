package com.fiap.azure.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Resource {
    private String id;
    private String name;
    private String type;
    private String location;
    private String resourceGroup;
}
