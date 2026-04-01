package com.coralio.chatbotmicroservice.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Currency {
    private String code;
    private String symbol;
    private String name;
    private double rate;
}