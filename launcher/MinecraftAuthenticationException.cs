using System.Net;

namespace LarpClient;

public enum AuthenticationStage { XboxLive, XboxSecurity, MinecraftLogin, JavaProfile }
public enum AuthenticationFailure { AccessDenied, ApplicationNotApproved, JavaProfileMissing, ServiceUnavailable }

public sealed class MinecraftAuthenticationException(AuthenticationStage stage, AuthenticationFailure failure, HttpStatusCode status, string message)
    : InvalidOperationException(message)
{
    public AuthenticationStage Stage { get; } = stage;
    public AuthenticationFailure Failure { get; } = failure;
    public HttpStatusCode Status { get; } = status;
}
