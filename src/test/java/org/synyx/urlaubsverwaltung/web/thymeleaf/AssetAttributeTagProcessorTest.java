package org.synyx.urlaubsverwaltung.web.thymeleaf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.processor.element.IElementTagStructureHandler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetAttributeTagProcessorTest {

    @Test
    void ensureDoProcessCallsAssetFilenameHashMapperAndSetsTheHashedValue() {
        final AssetFilenameHashMapper assetFilenameHashMapper = mock(AssetFilenameHashMapper.class);
        final AssetAttributeTagProcessor assetAttributeTagProcessor = new AssetAttributeTagProcessor("uv", "href", assetFilenameHashMapper);

        when(assetFilenameHashMapper.getHashedAssetFilename("awesome-attribute-value"))
            .thenReturn("value-with-hash");

        final IElementTagStructureHandler structureHandler = mock(IElementTagStructureHandler.class);
        assetAttributeTagProcessor.doProcess(null, null, null, "awesome-attribute-value", structureHandler);

        verify(structureHandler).setAttribute("href", "value-with-hash");
    }
}
