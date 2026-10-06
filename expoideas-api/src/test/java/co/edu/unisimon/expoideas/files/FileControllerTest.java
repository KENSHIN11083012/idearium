package co.edu.unisimon.expoideas.files;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import co.edu.unisimon.expoideas.support.TestData;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SecuredWebMvcTest(FileController.class)
class FileControllerTest {

    private static final UUID ID = UUID.fromString("0b0f3f7e-8c1a-4c55-9d3e-2f1a6b7c8d9e");
    private static final String SHA = "a".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    private void exists(boolean isPublic) {
        when(fileService.open(eq(ID), any()))
                .thenReturn(new FileContent(
                        "mi foto.png",
                        "image/png",
                        TestData.PNG.length,
                        SHA,
                        isPublic,
                        new ByteArrayResource(TestData.PNG)));
    }

    @Test
    void publicFileIsServedWithoutSessionAndLongCache() throws Exception {
        exists(true);

        mockMvc.perform(get("/api/v1/files/" + ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(TestData.PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + SHA + "\""))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''mi%20foto.png")))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void browserThatAlreadyHasItGets304() throws Exception {
        exists(true);

        mockMvc.perform(get("/api/v1/files/" + ID).header(HttpHeaders.IF_NONE_MATCH, "\"" + SHA + "\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void privateFileIsNotCached() throws Exception {
        exists(false);

        mockMvc.perform(get("/api/v1/files/" + ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void missingOrForbiddenIs404() throws Exception {
        when(fileService.open(eq(ID), any())).thenThrow(new NoSuchElementException("El archivo no existe"));

        mockMvc.perform(get("/api/v1/files/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El archivo no existe"));
    }

    @Test
    void malformedIdentifierIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/files/../../etc/passwd")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/files/no-es-un-uuid")).andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }
}
