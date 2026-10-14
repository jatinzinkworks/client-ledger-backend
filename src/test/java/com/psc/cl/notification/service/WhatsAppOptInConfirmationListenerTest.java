package com.psc.cl.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.model.ClientOptedInToWhatsApp;
import com.psc.cl.notification.exception.InvalidWhatsAppNumberException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class WhatsAppOptInConfirmationListenerTest {

    private static final UUID CLIENT_ID = UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35");

    @Mock
    private WhatsAppNotificationService whatsAppNotificationService;

    @InjectMocks
    private WhatsAppOptInConfirmationListener listener;

    @Test
    @DisplayName("confirms the opt-in of the client the event names")
    void confirmsOptIn() {
        when(whatsAppNotificationService.sendOptInConfirmation(CLIENT_ID)).thenReturn(Optional.of("wamid.OPTIN"));

        listener.confirm(new ClientOptedInToWhatsApp(CLIENT_ID));

        verify(whatsAppNotificationService).sendOptInConfirmation(CLIENT_ID);
    }

    @Test
    @DisplayName("runs in the background, and only once the opt-in has been committed")
    void runsInBackgroundAfterCommit() throws NoSuchMethodException {
        Method confirm = WhatsAppOptInConfirmationListener.class.getMethod("confirm", ClientOptedInToWhatsApp.class);

        assertThat(confirm.isAnnotationPresent(Async.class)).isTrue();
        assertThat(confirm.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("lets a confirmation that fails go, so the opt-in already saved stands")
    void toleratesFailure() {
        when(whatsAppNotificationService.sendOptInConfirmation(CLIENT_ID))
                .thenThrow(new InvalidWhatsAppNumberException(CLIENT_ID));

        assertThatCode(() -> listener.confirm(new ClientOptedInToWhatsApp(CLIENT_ID))).doesNotThrowAnyException();
    }
}
