package com.fiap.azure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionWithResourcesDTO {
    private String id;
    private String name;
    private String displayName;
    private String state;
    private int resourceCount;
}
