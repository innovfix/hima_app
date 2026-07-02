package com.gmwapp.hima.activities

import com.gmwapp.hima.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Native, card-based Terms & Conditions screen. Content mirrors
 * web.himaapp.in/terms.html.
 */
@AndroidEntryPoint
class TermsPolicyActivity : BasePolicyActivity() {

    override fun config() = PolicyConfig(
        toolbarTitle = getString(R.string.terms_and_condition),
        hero = Hero(
            iconRes = R.drawable.ic_clipboard_check,
            title = "Terms & Conditions",
            subtitle = "Welcome to Hi ma – Expert Connect, owned and operated by Innovfix Private Limited. By using our app, you agree to be bound by these Terms.",
        ),
        sections = listOf(
            Section(
                R.drawable.ic_info,
                "1. Introduction",
                listOf(
                    Block.Para("Welcome to Hi ma – Expert Connect (\"Platform\"). These Terms and Conditions outline the rules and regulations for the use of the application Hi ma, owned and operated by Innovfix Private Limited. By accessing or using our app, you agree to be bound by these Terms and Conditions. If you do not agree with any part of these Terms, please refrain from using our app."),
                ),
            ),
            Section(
                R.drawable.ic_user_add,
                "2. Eligibility",
                listOf(
                    Block.Bullet("You must be at least 18 years old to use Hi ma."),
                    Block.Bullet("You must provide accurate and complete information when registering."),
                    Block.Bullet("The platform is intended for professional networking and knowledge-sharing purposes."),
                ),
            ),
            Section(
                R.drawable.ic_lock_closed,
                "3. Account Responsibilities",
                listOf(
                    Block.Bullet("You are responsible for maintaining the confidentiality of your account credentials."),
                    Block.Bullet("You must not share your account or impersonate another person."),
                    Block.Bullet("Any misuse of the platform may result in account suspension or termination."),
                ),
            ),
            Section(
                R.drawable.ic_security,
                "4. Acceptable Use",
                listOf(
                    Block.Bullet("Users must engage respectfully and professionally."),
                    Block.Bullet("Harassment, hate speech, or offensive behavior will not be tolerated."),
                    Block.Bullet("Spam, promotions, or solicitation of services without approval is prohibited."),
                ),
            ),
            Section(
                R.drawable.ic_clipboard_check,
                "5. Content and Intellectual Property",
                listOf(
                    Block.Bullet("Users retain ownership of the content they share but grant Hi ma a non-exclusive license to display and share it within the platform."),
                    Block.Bullet("Do not post copyrighted or proprietary materials without authorization."),
                    Block.Bullet("Hi ma reserves the right to remove any content that violates these Terms."),
                ),
            ),
            Section(
                R.drawable.ic_privacy_modern,
                "6. Privacy and Data Protection",
                listOf(
                    Block.Bullet("We value your privacy. Our data practices are outlined in our Privacy Policy."),
                    Block.Bullet("Personal information will not be shared with third parties without consent."),
                    Block.Bullet("By using Hi ma, you agree to data collection for platform functionality and improvements."),
                ),
            ),
            Section(
                R.drawable.ic_info_outline,
                "7. Limitation of Liability",
                listOf(
                    Block.Bullet("Hi ma is not responsible for the accuracy of content shared by users."),
                    Block.Bullet("We do not guarantee uninterrupted or error-free service."),
                    Block.Bullet("Users are responsible for their interactions and collaborations on the platform."),
                ),
            ),
            Section(
                R.drawable.ic_warning,
                "8. Termination of Service",
                listOf(
                    Block.Bullet("Hi ma reserves the right to suspend or terminate accounts violating these Terms."),
                    Block.Bullet("Users may request account deletion at any time."),
                ),
            ),
            Section(
                R.drawable.ic_info,
                "9. Changes to Terms",
                listOf(
                    Block.Bullet("These Terms may be updated periodically."),
                    Block.Bullet("Continued use of the platform after updates constitutes acceptance of the revised Terms."),
                ),
            ),
            Section(
                R.drawable.ic_check_circle,
                "Acknowledgement",
                listOf(
                    Block.Para("By using Hi ma – Expert Connect, you acknowledge that you have read, understood, and agreed to these Terms and Conditions."),
                ),
                accentBg = "#E8F5E9",
                accentTint = "#2E7D32",
            ),
        ),
        contact = Contact(
            intro = "For questions or support, please contact us at:",
            email = "himaapp000@gmail.com",
            address = "Innovfix Private Limited,\nIndiqube Ascent, Municipal No. 420, PID68-6-420,\nIV Block, Koramangala, Bangalore South,\nBangalore - 560034, Karnataka, India.",
        ),
    )
}
