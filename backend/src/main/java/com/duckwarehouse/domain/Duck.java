package com.duckwarehouse.domain;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document("ducks")
public record Duck(@Id Integer id, Color color, Size size,
                   @Field(targetType = FieldType.DECIMAL128) BigDecimal price,
                   int quantity, boolean deleted, long version) {}
