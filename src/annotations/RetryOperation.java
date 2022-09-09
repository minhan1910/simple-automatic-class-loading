package annotations;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RetryOperation {
    Class<? extends Throwable>[] retryExceptions() default { Exception.class };

    long durationBetweenRetriesMs() default 0;

    String failureMessage() default "Operation failed after retryinng";

    int numberOfRetries();
}
