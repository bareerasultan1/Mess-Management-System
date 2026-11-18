package com.giki.mess.campus;

import org.junit.jupiter.api.Test;

class RulesTest {
    @Test void backgroundSaturation() throws Exception { WorkerChecks.main(); }
    @Test void boundariesAndTranslation() { DomainChecks.main(); }
}
