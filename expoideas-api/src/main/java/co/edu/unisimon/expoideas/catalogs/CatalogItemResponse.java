package co.edu.unisimon.expoideas.catalogs;

/** Registro de un catálogo que solo tiene nombre. */
public record CatalogItemResponse(Integer id, String name) {

    public static CatalogItemResponse from(CatalogItem item) {
        return new CatalogItemResponse(item.getId(), item.getName());
    }
}
