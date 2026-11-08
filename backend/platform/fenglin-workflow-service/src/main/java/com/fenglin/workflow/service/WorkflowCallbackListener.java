package com.fenglin.workflow.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkflowCallbackListener {

    private final WorkflowCallbackService callbackService;

    public WorkflowCallbackListener(WorkflowCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStateChanged(WorkflowStateChangedEvent event) {
        callbackService.dispatch(event.tenantId(), event.instanceId(), event.action());
    }
}
