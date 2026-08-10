package com.ikunkk02.wishingwillow.execution.action;

import com.ikunkk02.wishingwillow.execution.WishActionResult;
import com.ikunkk02.wishingwillow.execution.WishExecutionContext;

public interface WishActionExecutor {
    WishActionResult validate(WishExecutionContext context);
    WishActionResult execute(WishExecutionContext context);
}
