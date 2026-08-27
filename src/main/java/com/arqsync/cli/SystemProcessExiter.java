package com.arqsync.cli;

import org.springframework.stereotype.Component;

@Component
public class SystemProcessExiter implements ProcessExiter {

    @Override
    public void exit(int code) {
        System.exit(code);
    }
}
