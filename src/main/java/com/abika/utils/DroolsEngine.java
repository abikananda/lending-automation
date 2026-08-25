package com.abika.utils;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DroolsEngine {
    private final KieSession kieSession;
    private static final Logger logger = LoggerFactory.getLogger(DroolsEngine.class);

    public DroolsEngine() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            KieFileSystem kfs = kieServices.newKieFileSystem();
            kfs.write(ResourceFactory.newClassPathResource("rules/premium-rules.drl"));
            ReleaseId releaseId = kieServices.newReleaseId("com.example", "my-rules", "1.0.0");
            kfs.generateAndWritePomXML(releaseId);
            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();
            if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
                throw new RuntimeException("Build Errors:\n" + kieBuilder.getResults().toString());
            }
            KieContainer kieContainer = kieServices.newKieContainer(releaseId);
            this.kieSession = kieContainer.newKieSession();
            // Set the global logger so rule consequences can use it
            this.kieSession.setGlobal("logger", logger);
            logger.info("✅ Drools engine initialized");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Drools engine", e);
        }
    }

    public void applyRules(Object fact) {
        kieSession.insert(fact);
        kieSession.fireAllRules();
    }

    /**
     * Execute only the rule with the given name.
     * Clears session facts after rule execution to prevent memory accumulation
     * @param fact The fact to insert (e.g., Borrower)
     * @param ruleName The exact rule name to fire
     * @return true if rule executed, false otherwise
     */
    public boolean fireRuleByName(Object fact, String ruleName) {
        long startTime = System.currentTimeMillis();

        kieSession.insert(fact);

        final boolean[] ruleFired = {false};

        kieSession.fireAllRules(new AgendaFilter() {
            @Override
            public boolean accept(Match match) {
                if (match.getRule().getName().equals(ruleName)) {
                    ruleFired[0] = true;
                    return true;
                }
                return false;
            }
        });

        // Clear all facts from session after rule evaluation to prevent memory leak
        // Without this, facts accumulate and cause memory pressure after 500+ borrowers
        for (FactHandle handle : kieSession.getFactHandles()) {
            kieSession.delete(handle);
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Drools rule '{}' evaluated in {}ms (facts cleared)", ruleName, duration);

        return ruleFired[0];
    }

    /**
     * Dispose of the Drools session (call on shutdown)
     */
    public void dispose() {
        if (kieSession != null) {
            kieSession.dispose();
            logger.info("✅ Drools session disposed");
        }
    }
}
