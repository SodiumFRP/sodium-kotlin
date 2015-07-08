package sodium.impl

import sodium.Listener

abstract class ListenerImpl : Listener {
    var next: ListenerImpl? = null
}
