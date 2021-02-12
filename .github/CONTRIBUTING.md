# Raising an issue

Here is a template for raising an issue, copy and paste it into the text field and fill it:

```
## Steps to reproduce



## Environment

Device:
Browser:
OS:
URL:
```

# Opening a pull request

## General

We welcome code submissions from other teams. Here are the rules of engagement:

- The philosophy here is about communication, taking responsibility for your changes, and fast, incremental delivery.
- Speak to the team before you decide to do anything major. We can probably help design the change to maximise the chances of it being accepted.
- Our CSS and JS formatting conventions are enforced by [Prettier](https://prettier.io/).
- Pull requests made to main assume the change is ready to be pushed to `PROD`.
- Many small requests will be reviewed/merged quicker than a giant lists of changes.
- If you have a proposal, or want feedback on a branch under development, prefix `[WIP]` to the pull request title.

## Submission

### Guardian employees

This is applicable to [GMG employees](http://www.gmgplc.co.uk/).

1. Fork or clone the repo and make your changes.
2. Test your branch locally by running tests:
    - `./sbt project <project>/test`
3. Check and fix any formatting issues in the JS and CSS by running Prettier:
    - `yarn run prettier`
4. Open a pull request:
    - Explain why you are making this change in the pull request
5. A member of the team will review the changes. Once they are satisfied they will approve the pull request.
6. If there are no broken or ongoing builds in [TeamCity](https://teamcity.gutools.co.uk/viewType.html?buildTypeId=Tools_SecurityHq), merge your branch and then ensure the main branch is built successfully.
7. Your change will then be automatically deployed to `PROD` by - [Riff-Raff](https://riffraff.gutools.co.uk/).

### External contributions

Firstly, thanks for helping make our service better! Secondly, we'll try and make this as simple as possible.

- Fork the project on GitHub, patch the code, and submit a pull request.
- We will test, verify and merge your changes and then deploy the code.
- Certain contributions may require a Contributor License Agreement.

Finally, have you considered [working for us](https://workforus.theguardian.com/index.php/search-jobs-and-apply/?search_paths%5B%5D=&query=developer)?

## The team is your conscience

Here's some wise words from [@gklopper](https://github.com/gklopper).

We only merge to main when the software is ready to go live. This will be different for each thing we do, it might be as simple as showing the person next to you that the typo has been fixed, or you might spend an afternoon with Security or InfoSec looking over your shoulder.

If in doubt ask the team, **the team is your conscience**.

Once deployed to `PROD` check that your software is doing what you expected.

If it is a bit late in the day or it is nearly lunch and you do not want to deploy to PROD immediately then do not merge to main.
