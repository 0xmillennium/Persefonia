package dev.persefonia.webadmin.media;

import dev.persefonia.medialibrary.application.admin.AdminUploadAssetResult;
import dev.persefonia.medialibrary.application.admin.AssetMetadataUpdateResult;
import dev.persefonia.medialibrary.application.admin.MediaAdminAssetDetails;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandError;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandGateway;
import dev.persefonia.medialibrary.application.admin.MediaAdminQueryService;
import dev.persefonia.medialibrary.application.upload.UploadValidationError;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class AdminMediaController {
    private final ObjectProvider<MediaAdminCommandGateway> commands;
    private final MediaAdminQueryService queries;
    private final MediaAdminActorResolver actors;
    private final AdminMediaFormMapper mapper;
    private final AdminMediaFormValidator validator;
    private final AdminMediaPageChromeFactory chrome;

    public AdminMediaController(
            ObjectProvider<MediaAdminCommandGateway> commands,
            MediaAdminQueryService queries,
            MediaAdminActorResolver actors,
            AdminMediaFormMapper mapper,
            AdminMediaFormValidator validator,
            AdminMediaPageChromeFactory chrome) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/media")
    public String list(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "uploaded", required = false) String uploaded,
            @RequestParam(name = "duplicate", required = false) String duplicate,
            @RequestParam(name = "saved", required = false) String saved,
            Model model) {
        model.addAttribute("page", new AdminMediaListPage(
                chrome.create(authentication, csrfToken),
                queries.listAssets(),
                flash(uploaded, duplicate, saved, null)));
        return "admin/media/list";
    }

    @GetMapping("/admin/media/new")
    public String newForm(Authentication authentication, CsrfToken csrfToken, Model model) {
        model.addAttribute("page", new AdminMediaUploadPage(
                chrome.create(authentication, csrfToken), new AdminMediaUploadForm(), List.of(), List.of()));
        return "admin/media/new";
    }

    @PostMapping("/admin/media")
    public String upload(
            Authentication authentication,
            CsrfToken csrfToken,
            @ModelAttribute AdminMediaUploadForm form,
            Model model) {
        List<AdminMediaFieldError> errors = validator.validateUpload(form);
        if (!errors.isEmpty()) {
            model.addAttribute("page", new AdminMediaUploadPage(
                    chrome.create(authentication, csrfToken), form, errors, List.of()));
            return "admin/media/new";
        }

        AdminUploadAssetResult result =
                commandGateway().upload(mapper.toUploadCommand(actors.resolve(authentication), form));
        if (result instanceof AdminUploadAssetResult.Created created) {
            String suffix = created.warningOptional().isPresent() ? "?processingFailed" : "?uploaded";
            return "redirect:/admin/media/" + created.assetId().value() + suffix;
        }
        if (result instanceof AdminUploadAssetResult.Duplicate duplicate) {
            return "redirect:/admin/media/" + duplicate.existingAssetId().value() + "?duplicate";
        }

        AdminUploadAssetResult.Rejected rejected = (AdminUploadAssetResult.Rejected) result;
        model.addAttribute("page", new AdminMediaUploadPage(
                chrome.create(authentication, csrfToken),
                form,
                uploadErrors(rejected.errors()),
                List.of()));
        return "admin/media/new";
    }

    @GetMapping("/admin/media/{assetId}")
    public String detail(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("assetId") String assetId,
            @RequestParam(name = "uploaded", required = false) String uploaded,
            @RequestParam(name = "duplicate", required = false) String duplicate,
            @RequestParam(name = "saved", required = false) String saved,
            @RequestParam(name = "processingFailed", required = false) String processingFailed,
            Model model) {
        MediaAdminAssetDetails asset = asset(parse(assetId));
        model.addAttribute("page", detailPage(
                authentication,
                csrfToken,
                asset,
                form(asset),
                List.of(),
                List.of(),
                flash(uploaded, duplicate, saved, processingFailed)));
        return "admin/media/detail";
    }

    @PostMapping("/admin/media/{assetId}")
    public String update(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("assetId") String assetId,
            @ModelAttribute AdminMediaMetadataForm form,
            Model model) {
        AssetId id = parse(assetId);
        MediaAdminAssetDetails asset = asset(id);
        List<AdminMediaFieldError> errors = validator.validateMetadata(form, asset);
        if (!errors.isEmpty()) {
            model.addAttribute("page", detailPage(
                    authentication, csrfToken, asset, form, errors, List.of(), null));
            return "admin/media/detail";
        }

        AssetMetadataUpdateResult result =
                commandGateway().updateMetadata(mapper.toMetadataCommand(actors.resolve(authentication), id, form));
        if (result instanceof AssetMetadataUpdateResult.Updated) {
            return "redirect:/admin/media/" + id.value() + "?saved";
        }
        if (result instanceof AssetMetadataUpdateResult.NotFound) {
            throw notFound();
        }

        AssetMetadataUpdateResult.Rejected rejected = (AssetMetadataUpdateResult.Rejected) result;
        model.addAttribute("page", detailPage(
                authentication, csrfToken, asset, form, commandErrors(rejected.errors()), List.of(), null));
        return "admin/media/detail";
    }

    private AdminMediaDetailPage detailPage(
            Authentication authentication,
            CsrfToken csrfToken,
            MediaAdminAssetDetails asset,
            AdminMediaMetadataForm form,
            List<AdminMediaFieldError> fieldErrors,
            List<String> globalErrors,
            AdminMediaFlashMessage flashMessage) {
        return new AdminMediaDetailPage(
                chrome.create(authentication, csrfToken),
                asset,
                form,
                fieldErrors,
                globalErrors,
                flashMessage);
    }

    private MediaAdminAssetDetails asset(AssetId id) {
        return queries.findAssetDetails(id).orElseThrow(AdminMediaController::notFound);
    }

    private MediaAdminCommandGateway commandGateway() {
        MediaAdminCommandGateway gateway = commands.getIfAvailable();
        if (gateway == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media admin commands unavailable.");
        }
        return gateway;
    }

    private static AdminMediaMetadataForm form(MediaAdminAssetDetails asset) {
        AdminMediaMetadataForm form = new AdminMediaMetadataForm();
        form.setVisibility(asset.summary().visibility().name());
        if (asset.summary().kind() == AssetKind.IMAGE) {
            form.setAltText(asset.altText() == null ? "" : asset.altText());
            form.setDecorative(asset.decorative());
        }
        return form;
    }

    private static List<AdminMediaFieldError> uploadErrors(List<UploadValidationError> errors) {
        return errors.stream()
                .map(error -> new AdminMediaFieldError("file", error.message()))
                .toList();
    }

    private static List<AdminMediaFieldError> commandErrors(List<MediaAdminCommandError> errors) {
        return errors.stream()
                .map(error -> new AdminMediaFieldError(error.field(), error.message()))
                .toList();
    }

    private static AdminMediaFlashMessage flash(String uploaded, String duplicate, String saved, String processingFailed) {
        if (uploaded != null) return new AdminMediaFlashMessage("Media uploaded.", false);
        if (duplicate != null) return new AdminMediaFlashMessage("This file already exists.", false);
        if (saved != null) return new AdminMediaFlashMessage("Media metadata saved.", false);
        if (processingFailed != null) return new AdminMediaFlashMessage("Media uploaded, but image processing failed.", true);
        return null;
    }

    private static AssetId parse(String value) {
        try {
            return AssetId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset was not found.");
    }
}
