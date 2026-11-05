package org.wit.vitasense.domain
import org.junit.Assert.*
import org.junit.Test
class TrustedContactValidatorTest {
    @Test fun validManualContact() { assertNull(TrustedContactValidator.error("Ava","Sister","+353 87 123 4567",null,true)) }
    @Test fun invalidPhone() { listOf("","call me","123","+1+2345678","1234567890123456").forEach { assertNotNull(TrustedContactValidator.error("Ava","Sister",it,null,true)) } }
    @Test fun missingConsent() { assertNotNull(TrustedContactValidator.error("Ava","Sister","123456789",null,false)) }
    @Test fun invalidEmail() { assertNotNull(TrustedContactValidator.error("Ava","Sister","123456789","invalid",true)) }
}
