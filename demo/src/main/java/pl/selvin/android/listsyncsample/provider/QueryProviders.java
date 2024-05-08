package pl.selvin.android.listsyncsample.provider;

import android.content.UriMatcher;
import android.net.Uri;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

import pl.selvin.android.autocontentprovider.content.ContentHelper;
import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.listsyncsample.provider.implementation.AbstractQueryProvider;

public class QueryProviders {
	private final UriMatcher uriMatcher;
	private final ArrayList<AbstractQueryProvider> providers = new ArrayList<>();

	@SafeVarargs
	public QueryProviders(ListProvider contentProvider, ContentHelper contentHelper, Logger logger,
						  Class<? extends AbstractQueryProvider>... providerClasses) {
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		try {
			for (Class<? extends AbstractQueryProvider> providerClass : providerClasses) {
				final Constructor<? extends AbstractQueryProvider> constructor = providerClass.getConstructor(ListProvider.class, ContentHelper.class,
						Logger.class, UriMatcher.class, int.class);
				providers.add(constructor.newInstance(contentProvider, contentHelper, logger, uriMatcher, providers.size() + 1));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public AbstractQueryProvider get(Uri uri) {
		final int code = uriMatcher.match(uri);
		if (code > 0) {
			return providers.get(code - 1);
		}
		return null;
	}
}