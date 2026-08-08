package com.quantrity.anttester;

final class AntReadinessResult {
    final AntReadiness readiness;
    final String reason;

    AntReadinessResult(AntReadiness readiness, String reason) {
        this.readiness = readiness;
        this.reason = reason;
    }
}
