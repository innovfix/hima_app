package com.gmwapp.hima.activities

import com.gmwapp.hima.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Native, card-based Privacy Policy screen. Content mirrors the policy served
 * by the backend so users see the same text in a premium, scrollable layout.
 */
@AndroidEntryPoint
class PrivacyPolicyActivity : BasePolicyActivity() {

    override fun config() = PolicyConfig(
        toolbarTitle = getString(R.string.privacy_policy),
        hero = Hero(
            iconRes = R.drawable.ic_wg_shield,
            title = "Your Privacy Matters",
            subtitle = "We use your personal information only to provide and improve our services. By using Hi ma, you agree to this policy.",
            badge = "Effective October 6, 2020",
        ),
        sections = listOf(
            Section(
                R.drawable.ic_privacy_modern,
                "Information Collection & Use",
                listOf(
                    Block.Para("While using our services, we may ask you to provide certain personally identifiable information that can be used to contact or identify you — such as your name and email address (\"Personal Information\"). We also collect non-identifiable information, your profile info and other account-associated details for marketing and analytical purposes."),
                    Block.Para("This includes cookies and other technologies to improve our users' experience and the overall performance of our services. We may share information with our third-party partners in certain cases."),
                    Block.SubHead("We use the information collected:"),
                    Block.Bullet("To communicate with you;"),
                    Block.Bullet("To improve our services and see the number of users;"),
                    Block.Bullet("To customize the services, advertisements and/or products we provide to you;"),
                    Block.Bullet("To assist with our product and service development;"),
                    Block.Bullet("To perform marketing analysis; and"),
                    Block.Bullet("For other purposes related to our business."),
                    Block.Para("When you create a Hi ma account, you may sign in using your email address or a Facebook, LinkedIn, Google or other account. This authorizes us to access your public information from those accounts, consistent with your privacy settings."),
                ),
            ),
            Section(
                R.drawable.ic_user_add,
                "With Whom We May Share Information",
                listOf(
                    Block.Para("To run our business and provide seamless customer service, we use third-party vendors such as payment processors, cloud/server providers, analytics providers, technology partners and marketing companies. These vendors are not permitted to share or use the information for any other purpose."),
                    Block.Para("Hi ma also reserves the right to share information under the following circumstances:"),
                    Block.Bullet("In response to subpoenas, court orders or legal proceedings; to establish or defend our legal rights, or as otherwise required by law;"),
                    Block.Bullet("To investigate or take action against illegal activity or suspected prohibited practices, or to protect the safety of our customers and the company;"),
                    Block.Bullet("Under corporate events such as divestiture, merger, acquisition, asset sale or bankruptcy."),
                    Block.Para("Other than the circumstances above, you will be notified before we share your personal information with any third party, and you may opt out. We may share anonymous, non-personal information with advertisers and investors to improve service quality."),
                ),
            ),
            Section(
                R.drawable.ic_info_outline,
                "Visiting From Outside the United States",
                listOf(
                    Block.Para("Regardless of your place of residence, Hi ma stores your information in the United States, where our central server and database are located. Although privacy laws in the U.S. may differ from those where you are visiting, protecting your privacy remains our priority."),
                ),
            ),
            Section(
                R.drawable.ic_info,
                "Log Data",
                listOf(
                    Block.Para("Like many service providers, we collect information that your browser sends whenever you use our services (\"Log Data\"). This may include your device's IP address, browser type and version, the pages you visit, and the date, time and duration of your visit."),
                    Block.Para("We may use third-party services such as Google Analytics to collect, monitor and analyze this data."),
                ),
            ),
            Section(
                R.drawable.ic_info_outline,
                "Do Not Track Disclosure (\"DNT\")",
                listOf(
                    Block.Para("We do not respond to DNT signals, as the definitions and common approaches for this policy are not yet fully defined. However, you can adjust your privacy preferences within your search engine and the accounts you use to create a Hi ma account."),
                ),
            ),
            Section(
                R.drawable.ic_info,
                "Communications",
                listOf(
                    Block.Para("We may use your Personal Information to contact you with newsletters, marketing or promotional materials and other important information. You may opt out of this service. Your continued use of the service after we post any changes to the Privacy Policy or Terms constitutes your acceptance of those changes."),
                ),
            ),
            Section(
                R.drawable.ic_info_outline,
                "Cookies",
                listOf(
                    Block.Para("Cookies are small files with data, which may include an anonymous unique identifier, sent to your browser and stored on your device. We use cookies to improve our services and follow which links you click; we or third parties may use this data to show you advertisements."),
                    Block.Para("You can instruct your browser to refuse all cookies or to indicate when a cookie is being sent. If you do not accept cookies, some portions of our services may not work properly. We will not retain your information after you delete your Hi ma account, though it may take some time to be completely removed."),
                ),
            ),
            Section(
                R.drawable.ic_security,
                "Security",
                listOf(
                    Block.Para("The security of your Personal Information is important to us, but no method of transmission over the Internet or electronic storage is 100% secure. While we use commercially acceptable means to protect your information, we cannot guarantee its absolute security."),
                    Block.Para("Photographs, details and comments you post — along with your profile picture and username — can be seen by other users. Please keep in mind what you choose to share. Once you delete your account, it will take some time to be completely removed from the system."),
                ),
            ),
            Section(
                R.drawable.ic_user_add,
                "Third-Party Accounts",
                listOf(
                    Block.Para("You may create a Hi ma account through email or an existing Facebook, LinkedIn or Google account. Hi ma does not store those account passwords, and you are free to cancel any social-network connection at any time. We do not access your third-party pictures, locations or statuses unless they are made public. We do not control and are not responsible for content in third-party accounts."),
                ),
            ),
            Section(
                R.drawable.link,
                "Third-Party Websites",
                listOf(
                    Block.Para("Our services may contain links to other websites for information or advertising. These websites do not operate under this Privacy Policy and we do not control them or the information they collect. You should review each third-party website's own Privacy Policy and Terms. Access those websites at your own risk."),
                ),
            ),
            Section(
                R.drawable.ic_info,
                "Changes To This Privacy Policy",
                listOf(
                    Block.Para("This Privacy Policy is effective as of October 6, 2020 and remains in effect except for future changes, which take effect immediately after being posted on this page."),
                    Block.Para("We reserve the right to update this Privacy Policy at any time, so please review it periodically. Your continued use of the service after changes are posted constitutes your acceptance of the modified policy. If you do not consent to the changes, you should stop using the services."),
                ),
            ),
            Section(
                R.drawable.ic_wg_shield,
                "Children's Privacy",
                listOf(
                    Block.Para("Anyone below the age of 18 should not use the services. We do not knowingly collect, maintain or use personal information from children under the age of 18."),
                ),
            ),
            Section(
                R.drawable.ic_check_circle,
                "Enforcement",
                listOf(
                    Block.Para("We regularly review our own compliance with this Privacy Policy. If you submit a formal written complaint with your contact information, we will do our best to resolve the issue."),
                ),
            ),
        ),
        contact = Contact(
            intro = "If you have any questions about this Privacy Policy, or notice any activity against it, please reach out to us:",
            email = "Himaapp000@gmail.com",
            address = "Innovfix Private Limited,\nIndiqube Ascent, Municipal No. 420, PID68-6-420,\nIV Block, Koramangala, Bangalore South,\nBangalore - 560034, Karnataka, India.",
        ),
    )
}
