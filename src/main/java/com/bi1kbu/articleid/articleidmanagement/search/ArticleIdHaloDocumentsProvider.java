package com.bi1kbu.articleid.articleidmanagement.search;

import com.bi1kbu.articleid.articleidmanagement.service.StateStorage;
import org.pf4j.Extension;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import run.halo.app.search.HaloDocument;
import run.halo.app.search.HaloDocumentsProvider;

@Component
@Extension
public class ArticleIdHaloDocumentsProvider implements HaloDocumentsProvider {

    private final StateStorage stateStorage;

    public ArticleIdHaloDocumentsProvider(StateStorage stateStorage) {
        this.stateStorage = stateStorage;
    }

    @Override
    public Flux<HaloDocument> fetchAll() {
        return Flux.fromIterable(stateStorage.read().getLedger())
            .map(ArticleIdSearchDocumentMapper::toDocument);
    }

    @Override
    public String getType() {
        return ArticleIdSearchDocumentMapper.DOC_TYPE;
    }
}
