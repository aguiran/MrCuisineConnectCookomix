package app.toque.core

import app.toque.core.machine.Accessory
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.StepProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conversion des pages cookomix.com (page d'essai inventée, même structure que le site). */
class CookomixTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.bufferedReader().use { it.readText() }

    @Test fun sitemaps() {
        val index = """<sitemapindex><sitemap><loc>https://www.cookomix.com/post-sitemap.xml</loc></sitemap>
            <sitemap><loc>https://www.cookomix.com/recipe-sitemap1.xml</loc></sitemap>
            <sitemap><loc>https://www.cookomix.com/recipe-sitemap2.xml</loc></sitemap>
            <sitemap><loc>https://www.cookomix.com/recipe_category-sitemap.xml</loc></sitemap></sitemapindex>"""
        assertEquals(listOf("https://www.cookomix.com/recipe-sitemap1.xml", "https://www.cookomix.com/recipe-sitemap2.xml"), Cookomix.recipeSitemaps(index))
        val xml = """<urlset><url><loc>https://www.cookomix.com/recettes/</loc></url>
            <url><loc>https://www.cookomix.com/recettes/soupe-thermomix/</loc><lastmod>2024-01-02T10:00:00+01:00</lastmod><image:image><image:loc>x.jpg</image:loc></image:image></url>
            <url><loc>https://www.cookomix.com/recettes/gateau-thermomix/</loc></url></urlset>"""
        val e = Cookomix.parseSitemap(xml)
        assertEquals(listOf("soupe-thermomix", "gateau-thermomix"), e.map { it.slug })
        assertEquals("2024-01-02T10:00:00+01:00", e[0].lastmod)
        assertEquals("", e[1].lastmod)
    }

    @Test fun recipe() {
        val res = Cookomix.parsePage(fixture("cookomix-page.html"), "https://www.cookomix.com/recettes/veloute-essai-thermomix/")
        val r = res.recipe!!
        assertEquals(Cookomix.Compat.ADAPTED, res.compat)
        assertEquals("ckx-123456", r.id)
        assertEquals("Velouté d’essai", r.title)
        assertEquals("Soupe", r.category)
        assertTrue(r.tags.containsAll(listOf("Cookomix", "Adaptée MCC", "Française")))
        assertEquals(4, r.servings)
        assertEquals(10, r.prepMinutes)
        assertEquals(35, r.totalMinutes)
        assertEquals("Cheffe d'essai", r.author)
        assertEquals(4.5, r.rating, 0.001)
        assertEquals(12, r.ratingCount)
        assertEquals("https://cdn.example.test/uploads/2024/01/veloute-essai-800x600.jpg", r.image)
        assertEquals(listOf("1 Oignon", "400 g Carottes", "500 ml Eau", "1 pincée Sel"), r.allIngredients.map { it.display })
        assertTrue(r.notes.contains("https://www.cookomix.com/recettes/veloute-essai-thermomix/"))
        assertTrue(r.notes.contains("98 °C arrondi à 95 °C"))
        assertTrue(r.notes.contains("vitesse 6 en chauffe ramenée à 3"))

        val machines = r.steps.mapNotNull { it.machine }
        // Mixer 5 s vitesse 5 : couvercle rattaché, texte adapté.
        val first = r.steps.first { it.machine != null }
        assertTrue(first.text, first.text == "Ajoutez le couvercle avec le gobelet doseur. Mixer.")
        assertEquals(5 to 0, machines[0].speed to machines[0].tempC)
        // 25 min 98 °C → 95 °C.
        assertEquals(95, machines[1].tempC)
        assertEquals(1500, machines[1].seconds)
        // Vitesse 0,5 inverse → vitesse 1 inverse.
        assertEquals(1, machines[2].speed)
        assertTrue(machines[2].reverse)
        // Vitesse 6 en chauffe → 3.
        assertEquals(3, machines[3].speed)
        assertEquals(90, machines[3].tempC)
        // Varoma posé + vitesse 1 → programme Vapeur.
        assertEquals(StepProgram.STEAM, machines[4].program)
        assertEquals(900, machines[4].seconds)
        // Pétrin 3 min 30 → 2 min + 1 min 30.
        assertEquals(listOf(StepProgram.KNEAD, StepProgram.KNEAD), machines.drop(5).take(2).map { it.program })
        assertEquals(listOf(120, 90), machines.drop(5).take(2).map { it.seconds })
        // Varoma sans le Varoma posé → 120 °C, réglage d'origine rappelé.
        assertEquals(StepProgram.MANUAL to 120, machines[7].program to machines[7].tempC)
        assertTrue(r.steps.single { it.machine === machines[7] }.text.endsWith("(Thermomix : 10 min / vapeur / sens inverse / vitesse 1)"))
        assertTrue(machines.all { it.valid })
        // Réglage modifié : le texte rappelle celui du Thermomix.
        assertTrue(r.steps.single { it.machine?.speed == 3 && it.machine?.tempC == 90 }.text.endsWith("(Thermomix : 1 min / 90 °C / vitesse 6)"))
        assertTrue(r.steps.filter { it.machine != null }.all { it.needsReview })
        // Balance pour les ingrédients en grammes, texte sans « Thermomix », minuteur retiré.
        assertEquals(400, r.steps.first { it.weigh != null }.weigh!!.grams)
        assertTrue(r.steps.none { it.text.replace("(Thermomix :", "").contains("Thermomix", ignoreCase = true) })
        assertTrue(r.steps.any { it.text == "Laisser reposer 10 min." })
        assertTrue(r.steps.any { it.text.contains("panier vapeur") })
        assertEquals(listOf("autre-recette-thermomix"), Cookomix.recipeSlugs(fixture("cookomix-page.html")))
    }


    @Test fun incompatible() {
        val html = fixture("cookomix-page.html").replace("\"mix_temp\":\"98\"", "\"mix_temp\":\"160\"")
        val res = Cookomix.parsePage(html, "https://www.cookomix.com/recettes/x/")
        assertNull(res.recipe)
        assertEquals(Cookomix.Compat.INCOMPATIBLE, res.compat)
        assertTrue(res.reasons.single().contains("160"))
        val unknown = fixture("cookomix-page.html").replace("\"mix_speed\":\"5\"", "\"mix_speed\":\"cuisson lente\"")
        assertEquals(Cookomix.Compat.INCOMPATIBLE, Cookomix.parsePage(unknown, "u").compat)
    }

    @Test fun compatibleAsIs() {
        val html = """<script type="application/ld+json">{"@type":"Recipe","name":"Purée","recipeIngredient":["Lait - 100 grammes"]}</script>
            <script>var stepbystep ={"title":"Purée","steps":[{"action":"mix","action_verb":"mixer","mix_duration":"20","mix_temp":"0","mix_speed":"4"}]};</script>"""
        val res = Cookomix.parsePage(html, "https://www.cookomix.com/recettes/puree-thermomix/")
        assertEquals(Cookomix.Compat.OK, res.compat)
        assertEquals("ckx-puree-thermomix", res.recipe!!.id)
        assertEquals(Accessory.BLADE, res.recipe!!.steps.single().machine!!.accessory)
        assertTrue("Adaptée MCC" !in res.recipe!!.tags)
    }

    @Test fun token() {
        val (ok, _) = Cookomix.parseToken("""{"token":"abc.def.ghi","user_email":"a@example.test","user_nicename":"essai","user_display_name":"Essai"}""")
        assertNotNull(ok)
        assertEquals("essai", ok!!.login)
        val (ko, msg) = Cookomix.parseToken("""{"code":"[jwt_auth] incorrect_password","message":"<strong>Erreur :</strong> mauvais","data":{"status":403}}""")
        assertNull(ko)
        assertEquals("mot de passe incorrect", msg)
        assertEquals("""{"username":"a@example.test","password":"p\"w"}""", Cookomix.tokenBody(" a@example.test ", "p\"w"))
        assertEquals(30, Cookomix.minutes("PT30M"))
        assertEquals(80, Cookomix.minutes("PT1H20M"))
    }
}
