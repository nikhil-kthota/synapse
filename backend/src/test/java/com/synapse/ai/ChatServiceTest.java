package com.synapse.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatServiceTest {

    @Test
    void extractsIdentifiersAndTheirPartsWithoutFillerWords() {
        assertThat(ChatService.terms("What does OwnerController do when processCreationForm fails?"))
            .contains("ownercontroller", "owner", "controller", "processcreationform", "process", "creation", "form", "fails")
            .doesNotContain("what", "does", "when");
    }

    @Test
    void showsOnlyTheElementsTheQuestionNames() {
        var owner = new ChatService.CallSiteGroup(1, "Owner", "CLASS", "src/Owner.java:48", java.util.List.of());
        var findOwner = new ChatService.CallSiteGroup(2, "findOwner", "METHOD", "src/OwnerController.java:64", java.util.List.of());
        var validator = new ChatService.CallSiteGroup(3, "PetValidator", "CLASS", "src/PetValidator.java:30", java.util.List.of());
        var validate = new ChatService.CallSiteGroup(4, "validate", "METHOD", "src/PetValidator.java:38", java.util.List.of());
        var testHelper = new ChatService.CallSiteGroup(5, "validate", "METHOD", "src/PetValidatorTests.java:75", java.util.List.of());
        var all = java.util.List.of(owner, findOwner, validator, validate, testHelper);

        assertThat(ChatService.namedInQuestion(all, "Where is findOwner called from?", null)).containsExactly(findOwner);
        assertThat(ChatService.namedInQuestion(all, "Where is the validate method of PetValidator called?", null))
            .containsExactly(validate);
        assertThat(ChatService.namedInQuestion(all, "Where is the Owner class created?", null)).containsExactly(owner);
    }

    @Test
    void splitsSnakeCaseAndKeepsDomainWords() {
        assertThat(ChatService.terms("How is the user_session token validated?"))
            .contains("user_session", "user", "session", "token", "validated")
            .doesNotContain("how", "the");
    }
}
