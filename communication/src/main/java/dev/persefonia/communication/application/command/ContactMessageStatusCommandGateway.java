package dev.persefonia.communication.application.command;

public interface ContactMessageStatusCommandGateway {
    UpdateContactMessageStatusResult update(UpdateContactMessageStatusCommand command);
}
