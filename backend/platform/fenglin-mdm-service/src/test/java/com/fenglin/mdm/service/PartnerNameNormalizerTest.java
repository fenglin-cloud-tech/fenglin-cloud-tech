package com.fenglin.mdm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 归一化判重口径单测：全半角、空格、括号备注、企业后缀、完全同名。
 */
class PartnerNameNormalizerTest {

    @Test
    void stripsCompanySuffixVariantsToSameKey() {
        String base = PartnerNameNormalizer.normalize("丰麟云创科技有限公司");
        assertEquals(base, PartnerNameNormalizer.normalize("丰麟云创科技有限责任公司"));
        assertEquals(base, PartnerNameNormalizer.normalize("丰麟云创科技股份有限公司"));
        assertEquals(base, PartnerNameNormalizer.normalize("丰麟云创科技集团"));
        assertEquals(base, PartnerNameNormalizer.normalize("丰麟云创科技"));
        assertEquals("丰麟云创科技", base);
    }

    @Test
    void removesParenthesesRemarks() {
        assertEquals("丰麟云创科技", PartnerNameNormalizer.normalize("丰麟云创（上海）科技有限公司"));
        assertEquals("丰麟云创科技", PartnerNameNormalizer.normalize("丰麟云创(上海)(浦东)科技有限公司"));
    }

    @Test
    void removesWhitespaceAndUnifiesWidth() {
        assertEquals("丰麟云创科技", PartnerNameNormalizer.normalize("  丰麟　云创  科技有限公司 "));
        assertEquals("fenglin", PartnerNameNormalizer.normalize("ＦＥＮＧＬＩＮ"));
        assertEquals(PartnerNameNormalizer.normalize("丰麟云创科技有限公司"),
                PartnerNameNormalizer.normalize("丰麟 云创 科技有限公司"));
    }

    @Test
    void nearNamesAreNotEqualSoTheyAreOnlyCandidates() {
        assertNotEquals(PartnerNameNormalizer.normalize("丰麟云创科技有限公司"),
                PartnerNameNormalizer.normalize("丰麟云创信息技术有限公司"));
    }

    @Test
    void dedupKeyIsTenantScoped() {
        String normalized = PartnerNameNormalizer.normalize("丰麟云创科技有限公司");
        assertEquals(PartnerNameNormalizer.dedupKey(9001L, normalized),
                PartnerNameNormalizer.dedupKey(9001L, normalized));
        assertNotEquals(PartnerNameNormalizer.dedupKey(9001L, normalized),
                PartnerNameNormalizer.dedupKey(9002L, normalized));
    }

    @Test
    void blankInputNormalizesToEmpty() {
        assertEquals("", PartnerNameNormalizer.normalize(null));
        assertEquals("", PartnerNameNormalizer.normalize("  （ ） "));
    }
}
