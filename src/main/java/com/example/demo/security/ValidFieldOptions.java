package com.example.demo.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidFieldOptionsValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFieldOptions {
    String message() default "Field options are required for this field type";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
